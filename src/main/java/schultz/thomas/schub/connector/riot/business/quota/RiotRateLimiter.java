package schultz.thomas.schub.connector.riot.business.quota;

import lombok.extern.slf4j.Slf4j;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotConnectorBusyException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

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
 * qu'il faille un ordonnanceur séparé.</p>
 *
 * <h2>Deux voies, un seul compteur</h2>
 *
 * <p>Les deux voies puisent dans les <em>mêmes</em> fenêtres : rien n'est doublé, et un 429
 * suspend les deux. Ce qui les sépare est la place dans la file et le délai d'abandon.</p>
 *
 * <p>Une voie {@link QuotaLane#BULK} ne réserve un créneau qu'en <em>supposant déjà servis</em>
 * les interactifs en attente : elle ne prend donc que ce qui reste après eux. La cession est
 * bornée par {@code quota.bulk-yield} — passé ce délai, une tâche de collecte reprend sa place
 * au premier créneau libre, sans quoi un flux interactif soutenu l'affamerait.</p>
 *
 * <p><strong>L'attente a lieu hors du verrou.</strong> Le point du défaut corrigé le 21-09 :
 * tant que l'ingestion dormait sous le verrou, un appel interactif ne pouvait même pas
 * <em>entrer</em> dans {@code acquire}. La consommation d'un créneau, elle, reste atomique :
 * c'est elle qui garantit que le quota global n'est jamais dépassé.</p>
 */
@Slf4j
public class RiotRateLimiter {

    private final RiotProperties.Quota quota;
    private final Clock clock;
    private final Sleeper sleeper;

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<Long> burstWindow = new ArrayDeque<>();
    private final Deque<Long> sustainedWindow = new ArrayDeque<>();

    /** Fin de la pénalité en cours, en millisecondes depuis l'époque. 0 = aucune. */
    private long penalisedUntil;

    /** Appels interactifs en attente d'un créneau. La collecte les compte devant elle. */
    private int waitingInteractive;

    public RiotRateLimiter(RiotProperties.Quota quota, Clock clock, Sleeper sleeper) {
        this.quota = quota;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Réserve le droit d'émettre une requête sur la voie demandée, en attendant s'il le faut.
     *
     * @throws RiotConnectorBusyException  voie interactive, aucun créneau avant
     *                                     {@code quota.interactive-timeout} : occupé, pas en panne.
     * @throws RiotQuotaExceededException  voie de collecte, au-delà de {@code quota.acquire-timeout}.
     */
    public void acquire(QuotaLane lane) {
        long start = clock.millis();
        long deadline = start + lane.timeout(quota).toMillis();
        boolean interactive = lane == QuotaLane.INTERACTIVE;

        if (interactive) {
            enterInteractiveQueue();
        }
        try {
            long wait;
            while ((wait = reserveOrWait(lane, start, deadline)) > 0) {
                pause(wait);
            }
        } finally {
            if (interactive) {
                leaveInteractiveQueue();
            }
        }
    }

    /** @return 0 si le créneau est pris, sinon le temps à attendre hors verrou avant de réessayer. */
    private long reserveOrWait(QuotaLane lane, long start, long deadline) {
        lock.lock();
        try {
            long now = clock.millis();
            prune(burstWindow, now, quota.getBurstWindow());
            prune(sustainedWindow, now, quota.getSustainedWindow());

            int ahead = reservedAhead(lane, now, start);
            long wait = waitNeeded(now, ahead);
            if (wait <= 0) {
                burstWindow.addLast(now);
                sustainedWindow.addLast(now);
                return 0;
            }

            long incompressible = ahead == 0 ? wait : waitNeeded(now, 0);
            if (now + incompressible > deadline) {
                throw refusal(lane, Duration.ofMillis(incompressible));
            }

            // Céder ne doit jamais faire dormir au-delà de la borne de cession, sinon la
            // garantie anti-famine se perdrait dans un sommeil calculé trop long.
            return ahead == 0 ? wait : Math.min(wait, quota.getBulkYield().toMillis() - (now - start));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Créneaux que l'appelant laisse devant lui.
     *
     * <p>Zéro pour l'interactif — il ne cède à personne. Pour la collecte, les interactifs en
     * attente, jusqu'à ce qu'elle cède depuis {@code bulk-yield} : au-delà elle reprend sa
     * place, et c'est cette borne qui l'empêche d'être affamée.</p>
     */
    private int reservedAhead(QuotaLane lane, long now, long start) {
        if (lane == QuotaLane.INTERACTIVE || now - start >= quota.getBulkYield().toMillis()) {
            return 0;
        }
        return waitingInteractive;
    }

    /** Millisecondes à attendre avant qu'un créneau soit libre, {@code ahead} déjà réservés. */
    private long waitNeeded(long now, int ahead) {
        if (penalisedUntil > now) {
            return penalisedUntil - now;
        }
        long burstWait = windowWait(burstWindow, now, effective(quota.getBurstRequests()),
                quota.getBurstWindow(), ahead);
        long sustainedWait = windowWait(sustainedWindow, now, effective(quota.getSustainedRequests()),
                quota.getSustainedWindow(), ahead);
        return Math.max(burstWait, sustainedWait);
    }

    private long windowWait(Deque<Long> window, long now, int limit, Duration span, int ahead) {
        int index = window.size() + ahead - limit;
        if (index < 0) {
            return 0;
        }
        if (index >= window.size()) {
            return span.toMillis();
        }
        return slotAt(window, index) + span.toMillis() - now;
    }

    private long slotAt(Deque<Long> window, int index) {
        Iterator<Long> slots = window.iterator();
        for (int i = 0; i < index; i++) {
            slots.next();
        }
        return slots.next();
    }

    private RiotQuotaExceededException refusal(QuotaLane lane, Duration wait) {
        if (lane == QuotaLane.INTERACTIVE) {
            return new RiotConnectorBusyException(
                    "Connecteur occupé : aucun créneau de quota Riot avant " + wait + ", au-delà des "
                            + quota.getInteractiveTimeout() + " accordées à un appel interactif.",
                    wait);
        }
        return new RiotQuotaExceededException(
                "Quota Riot saturé : il faudrait attendre " + wait + ", au-delà du délai accepté.",
                wait);
    }

    /**
     * Enregistre un 429 : tous les appels attendent le délai demandé par Riot, les deux voies
     * confondues — le quota est global à la clé, une pénalité par voie serait sans effet.
     *
     * <p>Le délai est plafonné. Un {@code Retry-After} aberrant — en-tête mal formé, proxy
     * fantaisiste — ne doit pas geler le connecteur pour une heure.</p>
     */
    public void penalise(Duration retryAfter) {
        Duration capped = retryAfter.compareTo(quota.getMaxRetryAfter()) > 0
                ? quota.getMaxRetryAfter()
                : retryAfter;
        lock.lock();
        try {
            long until = clock.millis() + Math.max(capped.toMillis(), 0);
            penalisedUntil = Math.max(penalisedUntil, until);
        } finally {
            lock.unlock();
        }
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
    public Duration throttledFor() {
        lock.lock();
        try {
            long remaining = penalisedUntil - clock.millis();
            return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
        } finally {
            lock.unlock();
        }
    }

    /** Appels interactifs en attente d'un créneau, pour la supervision. */
    public int interactiveWaiting() {
        lock.lock();
        try {
            return waitingInteractive;
        } finally {
            lock.unlock();
        }
    }

    /** Marge retirée de chaque fenêtre : viser exactement la limite, c'est la dépasser. */
    private int effective(int limit) {
        return Math.max(1, limit - quota.getSafetyMargin());
    }

    private void enterInteractiveQueue() {
        lock.lock();
        try {
            waitingInteractive++;
        } finally {
            lock.unlock();
        }
    }

    private void leaveInteractiveQueue() {
        lock.lock();
        try {
            waitingInteractive--;
        } finally {
            lock.unlock();
        }
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
