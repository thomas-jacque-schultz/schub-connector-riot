package schultz.thomas.schub.connector.riot.business.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import schultz.thomas.schub.connector.riot.config.RiotProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'arbitrage entre les deux voies, avec de vrais threads.
 *
 * <p>Ces propriétés ne se simulent pas avec une horloge à la main : elles tiennent à ce qu'un
 * thread voit de l'attente d'un autre. Les fenêtres sont raccourcies à quelques centaines de
 * millisecondes pour que le test reste court.</p>
 *
 * <p>Les créneaux se comptent par {@code granted}, daté sous le verrou du limiteur : un compteur
 * tenu par le test s'incrémenterait après le retour d'{@code acquire}, donc plusieurs
 * millisecondes trop tard sous vingt threads.</p>
 */
class RiotRateLimiterLanesTest {

    /** Écart toléré entre l'instant daté par le limiteur et celui relevé par le test. */
    private static final long TOLERANCE_MESURE_MS = 40;

    private final List<Thread> lances = new ArrayList<>();
    private final AtomicBoolean encore = new AtomicBoolean(true);

    @Test
    @Timeout(30)
    @DisplayName("un appel interactif passe devant une collecte déjà en file")
    void lInteractifPasseDevantLaCollecte() throws InterruptedException {
        RiotProperties.Quota quota = quota(4, Duration.ofMillis(200));
        quota.setBulkYield(Duration.ofSeconds(1));
        RiotRateLimiter limiteur = limiteur(quota);

        // Vingt tâches en file : en premier arrivé premier servi, elles valent cinq fenêtres.
        charger(20, QuotaLane.BULK, limiteur, null);
        attendreAuMoins(limiteur, QuotaLane.BULK, 8);

        // Un témoin de collecte entre en file juste AVANT l'appel interactif.
        AtomicLong temoinNanos = new AtomicLong(Long.MAX_VALUE);
        CountDownLatch enFile = new CountDownLatch(1);
        Thread temoin = temoinDeCollecte(limiteur, enFile, temoinNanos);
        enFile.await();

        long debut = System.nanoTime();
        limiteur.acquire(QuotaLane.INTERACTIVE);
        Duration attenteInteractive = Duration.ofNanos(System.nanoTime() - debut);

        temoin.join(5000);
        arreter();

        // Le quota est saturé : une fenêtre d'attente est incompressible. Ce qui ne l'est pas,
        // c'est la file — vingt tâches devant soi vaudraient cinq fenêtres.
        assertThat(attenteInteractive).isLessThan(Duration.ofMillis(400));
        assertThat(attenteInteractive).isLessThan(Duration.ofNanos(temoinNanos.get()));
    }

    @Test
    @Timeout(30)
    @DisplayName("la collecte avance malgré un flux interactif soutenu")
    void laCollecteNEstPasAffamee() throws InterruptedException {
        RiotProperties.Quota quota = quota(4, Duration.ofMillis(200));
        quota.setBulkYield(Duration.ofMillis(300));
        RiotRateLimiter limiteur = limiteur(quota);

        charger(8, QuotaLane.INTERACTIVE, limiteur, null);
        charger(1, QuotaLane.BULK, limiteur, null);

        Thread.sleep(1500);
        arreter();

        assertThat(limiteur.granted(QuotaLane.INTERACTIVE))
                .as("le flux interactif était bien soutenu").isGreaterThan(20);
        // Une cession dure au plus 300 ms : sur 1,5 s, la collecte reprend la main cinq fois.
        assertThat(limiteur.granted(QuotaLane.BULK)).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Timeout(30)
    @DisplayName("les deux voies réunies ne dépassent jamais le quota : un seul compteur")
    void leQuotaGlobalNEstJamaisDepasse() throws InterruptedException {
        int limite = 5;
        Duration fenetre = Duration.ofMillis(300);
        RiotRateLimiter limiteur = limiteur(quota(limite, fenetre));

        ConcurrentLinkedQueue<Long> instants = new ConcurrentLinkedQueue<>();
        charger(6, QuotaLane.INTERACTIVE, limiteur, instants);
        charger(6, QuotaLane.BULK, limiteur, instants);

        Thread.sleep(2000);
        arreter();

        List<Long> tries = instants.stream().sorted().toList();
        assertThat(tries).hasSizeGreaterThan(limite * 2);
        for (int i = 0; i + limite < tries.size(); i++) {
            assertThat(tries.get(i + limite) - tries.get(i))
                    .as("créneaux %d à %d, pris dans une même fenêtre", i, i + limite)
                    .isGreaterThanOrEqualTo(fenetre.toMillis() - TOLERANCE_MESURE_MS);
        }
    }

    private RiotProperties.Quota quota(int parFenetre, Duration fenetre) {
        RiotProperties.Quota quota = new RiotProperties.Quota();
        quota.setSafetyMargin(0);
        quota.setInteractiveReserve(0);
        quota.setBurstRequests(parFenetre);
        quota.setBurstWindow(fenetre);
        // La fenêtre longue est mise hors jeu : ce qu'on mesure ici est l'arbitrage, pas l'étalement.
        quota.setSustainedRequests(100_000);
        quota.setSustainedWindow(Duration.ofMinutes(2));
        quota.setInteractiveTimeout(Duration.ofSeconds(10));
        quota.setAcquireTimeout(Duration.ofSeconds(10));
        return quota;
    }

    private RiotRateLimiter limiteur(RiotProperties.Quota quota) {
        return new RiotRateLimiter(quota, Clock.systemUTC(), duree -> Thread.sleep(duree.toMillis()));
    }

    private void charger(int threads, QuotaLane voie, RiotRateLimiter limiteur,
                         ConcurrentLinkedQueue<Long> instants) {
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(() -> {
                while (encore.get()) {
                    try {
                        limiteur.acquire(voie);
                    } catch (RuntimeException abandon) {
                        return;
                    }
                    if (instants != null) {
                        instants.add(System.currentTimeMillis());
                    }
                }
            }, "quota-" + voie + "-" + i);
            thread.setDaemon(true);
            thread.start();
            lances.add(thread);
        }
    }

    private Thread temoinDeCollecte(RiotRateLimiter limiteur, CountDownLatch enFile, AtomicLong nanos) {
        Thread temoin = new Thread(() -> {
            long debut = System.nanoTime();
            enFile.countDown();
            try {
                limiteur.acquire(QuotaLane.BULK);
            } catch (RuntimeException abandon) {
                return;
            }
            nanos.set(System.nanoTime() - debut);
        }, "quota-temoin");
        temoin.setDaemon(true);
        temoin.start();
        return temoin;
    }

    private void attendreAuMoins(RiotRateLimiter limiteur, QuotaLane voie, long cible)
            throws InterruptedException {
        while (limiteur.granted(voie) < cible) {
            Thread.sleep(10);
        }
    }

    private void arreter() throws InterruptedException {
        encore.set(false);
        lances.forEach(Thread::interrupt);
        for (Thread thread : lances) {
            thread.join(2000);
        }
        lances.clear();
    }
}
