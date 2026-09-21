package schultz.thomas.schub.connector.riot.business.quota;

import lombok.extern.slf4j.Slf4j;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Le quota Riot, tenu ici et nulle part ailleurs.
 *
 * <p>C'est une contrainte du système externe : sa place est dans le connecteur, avec le cache.
 * Le cœur demande des parties, il ne planifie pas les appels.</p>
 *
 * <h2>Deux fenêtres, pas une</h2>
 *
 * <p>Relevé sur l'API réelle le 18-09 dans {@code X-App-Rate-Limit} : {@code 100:120,20:1} —
 * 20 requêtes par seconde <em>et</em> 100 par deux minutes. Ne tenir que la première laisse
 * partir un burst qui épuise la seconde ; ne tenir que la seconde laisse passer vingt appels
 * simultanés. Les deux fenêtres glissantes sont donc tenues ensemble.</p>
 *
 * <p><strong>C'est cette double borne qui <em>est</em> l'étalement du premier remplissage</strong> :
 * la fenêtre longue impose d'elle-même un régime moyen d'environ un appel toutes les 1,2 s, sans
 * qu'il faille un ordonnanceur séparé. Une fois l'historique constitué, un rafraîchissement ne
 * coûte que les parties nouvelles et la borne ne se fait plus sentir.</p>
 *
 * <h2>Le 429 et son {@code Retry-After}</h2>
 *
 * <p>Riot renvoie {@code Retry-After} (vérifié : {@code retry-after: 2} sur un 429 provoqué
 * volontairement). L'ignorer fait empirer la situation — les requêtes refusées comptent quand
 * même. Une pénalité met donc <em>tous</em> les appelants en attente, pas seulement celui qui a
 * pris le 429 : le quota est global à la clé, une pénalité locale serait sans effet.</p>
 *
 * <p>Les méthodes sont synchronisées et l'attente a lieu sous le verrou. C'est délibéré : la
 * sérialisation stricte est précisément ce qu'on cherche. Un connecteur qui pacerait « en
 * parallèle » ne pacerait rien.</p>
 */
@Slf4j
public class RiotRateLimiter {

    private final RiotProperties.Quota quota;
    private final Clock clock;
    private final Sleeper sleeper;

    private final Deque<Long> burstWindow = new ArrayDeque<>();
    private final Deque<Long> sustainedWindow = new ArrayDeque<>();

    /** Fin de la pénalité en cours, en millisecondes depuis l'époque. 0 = aucune. */
    private long penalisedUntil;

    public RiotRateLimiter(RiotProperties.Quota quota, Clock clock, Sleeper sleeper) {
        this.quota = quota;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Réserve le droit d'émettre une requête, en attendant s'il le faut.
     *
     * @throws RiotQuotaExceededException si l'attente dépasse {@code acquireTimeout} : à ce
     *                                    stade, faire patienter l'appelant davantage est moins
     *                                    honnête que lui dire de revenir.
     */
    public synchronized void acquire() {
        long deadline = clock.millis() + quota.getAcquireTimeout().toMillis();

        while (true) {
            long now = clock.millis();
            prune(burstWindow, now, quota.getBurstWindow());
            prune(sustainedWindow, now, quota.getSustainedWindow());

            long waitMillis = waitNeeded(now);
            if (waitMillis <= 0) {
                burstWindow.addLast(now);
                sustainedWindow.addLast(now);
                return;
            }

            if (now + waitMillis > deadline) {
                throw new RiotQuotaExceededException(
                        "Quota Riot saturé : il faudrait attendre " + Duration.ofMillis(waitMillis)
                                + ", au-delà du délai accepté.",
                        Duration.ofMillis(waitMillis));
            }
            pause(waitMillis);
        }
    }

    /** Combien de millisecondes il faut attendre avant de pouvoir émettre, 0 si c'est libre. */
    private long waitNeeded(long now) {
        if (penalisedUntil > now) {
            return penalisedUntil - now;
        }
        long burstWait = windowWait(burstWindow, now, effective(quota.getBurstRequests()), quota.getBurstWindow());
        long sustainedWait = windowWait(sustainedWindow, now, effective(quota.getSustainedRequests()),
                quota.getSustainedWindow());
        return Math.max(burstWait, sustainedWait);
    }

    private long windowWait(Deque<Long> window, long now, int limit, Duration span) {
        if (window.size() < limit) {
            return 0;
        }
        Long oldest = window.peekFirst();
        return oldest == null ? 0 : oldest + span.toMillis() - now;
    }

    /**
     * Enregistre un 429 : tous les appels attendent le délai demandé par Riot.
     *
     * <p>Le délai est plafonné. Un {@code Retry-After} aberrant — en-tête mal formé, proxy
     * fantaisiste — ne doit pas geler le connecteur pour une heure.</p>
     */
    public synchronized void penalise(Duration retryAfter) {
        Duration capped = retryAfter.compareTo(quota.getMaxRetryAfter()) > 0
                ? quota.getMaxRetryAfter()
                : retryAfter;
        long until = clock.millis() + Math.max(capped.toMillis(), 0);
        penalisedUntil = Math.max(penalisedUntil, until);
        log.warn("429 de Riot : toutes les requêtes sont suspendues pendant {}", capped);
    }

    /**
     * Le débit soutenable, marge déduite : le minimum des deux fenêtres.
     *
     * <p>Exposé parce que c'est lui, et rien d'autre, qui donne le temps d'écoulement d'une
     * file d'ingestion. Le calculer ailleurs dupliquerait la règle de marge.</p>
     */
    public double allowedPerMinute() {
        double burst = effective(quota.getBurstRequests()) * 60_000.0 / quota.getBurstWindow().toMillis();
        double sustained = effective(quota.getSustainedRequests()) * 60_000.0
                / quota.getSustainedWindow().toMillis();
        return Math.min(burst, sustained);
    }

    /** Ce qui reste à purger d'une pénalité 429, {@link Duration#ZERO} si aucune. */
    public synchronized Duration throttledFor() {
        long remaining = penalisedUntil - clock.millis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    /** Marge retirée de chaque fenêtre : viser exactement la limite, c'est la dépasser. */
    private int effective(int limit) {
        return Math.max(1, limit - quota.getSafetyMargin());
    }

    private void prune(Deque<Long> window, long now, Duration span) {
        long horizon = now - span.toMillis();
        while (!window.isEmpty() && window.peekFirst() <= horizon) {
            window.removeFirst();
        }
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(Duration.ofMillis(millis));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RiotQuotaExceededException("Attente du quota Riot interrompue.", Duration.ZERO);
        }
    }
}
