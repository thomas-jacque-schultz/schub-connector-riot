package schultz.thomas.schub.connector.riot.business.quota;

import lombok.extern.slf4j.Slf4j;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotConnectorBusyException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
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
 * les interactifs en attente : elle ne prend donc que ce qui reste après eux.</p>
 *
 * <p>La cession est bornée par {@code quota.bulk-yield}, et la règle s'inverse alors : la tâche
 * de collecte cesse de compter les interactifs devant elle, et ce sont eux qui la comptent. Ne
 * faire que la première moitié ne suffit pas — mesuré : un ouvrier seul face à huit appelants
 * interactifs perd la course au créneau libéré et n'obtient qu'un créneau en 1,5 s là où la
 * borne lui en promet cinq. Les deux états s'excluent, aucun blocage mutuel n'est possible.</p>
 *
 * <h2>Une réserve, et pas seulement une cession</h2>
 *
 * <p>Céder ne sert qu'à qui attend déjà. Mesuré le 22-09 sous 2 277 tâches en file : aucun des
 * vingt appels interactifs n'est servi, la collecte ayant consommé la fenêtre soutenue entière
 * avant qu'ils n'arrivent — il ne restait plus rien à céder. La collecte s'arrête donc
 * {@code quota.interactive-reserve} créneaux avant la limite, et espace les siens tant que la
 * réserve est armée : une fenêtre prise d'un bloc ne libère plus rien pendant cent secondes.
 * Sans demande interactive depuis {@code quota.interactive-reserve-idle}, la collecte reprend
 * la réserve, à un créneau près.</p>
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

    /** Créneaux accordés par voie, depuis le démarrage. Le partage réel ne se déduit pas d'ailleurs. */
    private final EnumMap<QuotaLane, Long> granted = new EnumMap<>(QuotaLane.class);

    /** Appels interactifs en attente d'un créneau. La collecte les compte devant elle. */
    private int waitingInteractive;

    /** Tâches de collecte ayant cédé au-delà de {@code bulk-yield}. L'interactif les laisse passer. */
    private int starvedBulk;

    /** Dernière demande interactive, servie ou non. 0 = aucune depuis le démarrage. */
    private long lastInteractiveDemand;

    /** Dernier créneau accordé à la collecte, pour l'espacement de sa voie. */
    private long lastBulkGrant;

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

        boolean claimedPriority = false;
        if (interactive) {
            enqueue(true);
        }
        try {
            while (true) {
                if (!interactive && !claimedPriority && yieldElapsed(clock.millis(), start)) {
                    enqueue(false);
                    claimedPriority = true;
                }
                long wait = reserveOrWait(lane, start, deadline);
                if (wait <= 0) {
                    return;
                }
                pause(wait);
            }
        } finally {
            if (interactive) {
                dequeue(true);
            } else if (claimedPriority) {
                dequeue(false);
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
            long wait = waitNeeded(now, lane, ahead);
            if (wait <= 0) {
                burstWindow.addLast(now);
                sustainedWindow.addLast(now);
                granted.merge(lane, 1L, Long::sum);
                if (lane == QuotaLane.BULK) {
                    lastBulkGrant = now;
                }
                return 0;
            }

            long incompressible = ahead == 0 ? wait : waitNeeded(now, lane, 0);
            if (now + incompressible > deadline) {
                throw refusal(lane, Duration.ofMillis(incompressible));
            }

            long capped = Math.min(wait, deadline - now);
            if (lane == QuotaLane.BULK && ahead > 0) {
                // Céder ne doit jamais faire dormir au-delà de la borne de cession, sinon la
                // garantie anti-famine se perdrait dans un sommeil calculé trop long.
                capped = Math.min(capped, quota.getBulkYield().toMillis() - (now - start));
            }
            return Math.max(1, capped);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Créneaux que l'appelant laisse devant lui.
     *
     * <p>La collecte compte les interactifs en attente — c'est la priorité. Passé
     * {@code bulk-yield} de cession, elle cesse de les compter et devient à son tour comptée par
     * eux : sans cette réciprocité, un seul ouvrier face à un flux interactif soutenu perdrait
     * indéfiniment la course au créneau libéré. Les deux états s'excluent, donc aucun blocage
     * mutuel n'est possible.</p>
     */
    private int reservedAhead(QuotaLane lane, long now, long start) {
        if (lane == QuotaLane.INTERACTIVE) {
            return starvedBulk;
        }
        return yieldElapsed(now, start) ? 0 : waitingInteractive;
    }

    private boolean yieldElapsed(long now, long start) {
        return now - start >= quota.getBulkYield().toMillis();
    }

    /** Millisecondes à attendre avant qu'un créneau soit libre, {@code ahead} déjà réservés. */
    private long waitNeeded(long now, QuotaLane lane, int ahead) {
        if (penalisedUntil > now) {
            return penalisedUntil - now;
        }
        long burstWait = windowWait(burstWindow, now, effective(quota.getBurstRequests()),
                quota.getBurstWindow(), ahead);
        long sustainedWait = windowWait(sustainedWindow, now, sustainedLimit(lane, now),
                quota.getSustainedWindow(), ahead);
        return Math.max(Math.max(burstWait, sustainedWait), spacingWait(lane, now));
    }

    /** La collecte s'arrête avant les derniers créneaux de la fenêtre soutenue ; l'interactif, non. */
    private int sustainedLimit(QuotaLane lane, long now) {
        int limit = effective(quota.getSustainedRequests());
        return lane == QuotaLane.INTERACTIVE ? limit : Math.max(1, limit - reserve(now));
    }

    /**
     * La réserve en vigueur.
     *
     * <p>Rendre le dernier créneau ferait attendre la fenêtre entière à la demande qui revient :
     * la collecte aurait rempli les deux minutes, et l'armement n'a pas d'effet rétroactif. Ce
     * créneau est le prix du réarmement immédiat.</p>
     */
    private int reserve(long now) {
        int configured = configuredReserve();
        if (configured == 0) {
            return 0;
        }
        boolean demanded = lastInteractiveDemand > 0
                && now - lastInteractiveDemand <= quota.getInteractiveReserveIdle().toMillis();
        return demanded ? configured : 1;
    }

    private int configuredReserve() {
        return Math.max(0, Math.min(quota.getInteractiveReserve(),
                effective(quota.getSustainedRequests()) - 1));
    }

    /**
     * Espacement des créneaux de collecte, tant que la réserve est armée.
     *
     * <p>Une réserve seule ne suffit pas : la collecte prendrait sa part d'un bloc, et la fenêtre
     * ne libérerait plus rien pendant cent secondes — la réserve une fois consommée, l'interactif
     * attendrait ce bloc. Espacée, la collecte tient le même débit et la fenêtre libère en
     * continu.</p>
     */
    private long spacingWait(QuotaLane lane, long now) {
        int reserve = reserve(now);
        if (lane == QuotaLane.INTERACTIVE || reserve <= 1 || lastBulkGrant == 0) {
            return 0;
        }
        long spacing = quota.getSustainedWindow().toMillis()
                / Math.max(1, effective(quota.getSustainedRequests()) - reserve);
        return Math.max(0, lastBulkGrant + spacing - now);
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
     * Le débit soutenable de la collecte, marge et réserve déduites.
     *
     * <p>Exposé parce que c'est lui, et rien d'autre, qui donne le temps d'écoulement d'une
     * file d'ingestion. La réserve est comptée armée : une échéance annoncée doit être tenable
     * le jour où quelqu'un consulte.</p>
     */
    public double allowedPerMinute() {
        double burst = effective(quota.getBurstRequests()) * 60_000.0 / quota.getBurstWindow().toMillis();
        double sustained = (effective(quota.getSustainedRequests()) - configuredReserve())
                * 60_000.0 / quota.getSustainedWindow().toMillis();
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

    /** Créneaux accordés à une voie depuis le démarrage : c'est là que se lit le partage réel. */
    public long granted(QuotaLane lane) {
        lock.lock();
        try {
            return granted.getOrDefault(lane, 0L);
        } finally {
            lock.unlock();
        }
    }

    /** Marge retirée de chaque fenêtre : viser exactement la limite, c'est la dépasser. */
    private int effective(int limit) {
        return Math.max(1, limit - quota.getSafetyMargin());
    }

    private void enqueue(boolean interactive) {
        lock.lock();
        try {
            if (interactive) {
                waitingInteractive++;
                lastInteractiveDemand = clock.millis();
            } else {
                starvedBulk++;
            }
        } finally {
            lock.unlock();
        }
    }

    private void dequeue(boolean interactive) {
        lock.lock();
        try {
            if (interactive) {
                waitingInteractive--;
            } else {
                starvedBulk--;
            }
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
