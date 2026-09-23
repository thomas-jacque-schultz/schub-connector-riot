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
 * Clé de développement : X-App-Rate-Limit 100:120,20:1, deux fenêtres glissantes tenues ensemble.
 * Les deux voies puisent dans les mêmes fenêtres ; un 429 suspend les deux.
 * L'attente se fait hors du verrou, la consommation d'un créneau reste atomique.
 */
@Slf4j
public class RiotRateLimiter {

    private final RiotProperties.Quota quota;
    private final Clock clock;
    private final Sleeper sleeper;

    private final ReentrantLock lock = new ReentrantLock();
    private final Deque<Long> burstWindow = new ArrayDeque<>();
    private final Deque<Long> sustainedWindow = new ArrayDeque<>();

    private long penalisedUntil;

    private final EnumMap<QuotaLane, Long> granted = new EnumMap<>(QuotaLane.class);

    private int waitingInteractive;

    private int starvedBulk;

    private long lastInteractiveDemand;

    private long lastBulkGrant;

    public RiotRateLimiter(RiotProperties.Quota quota, Clock clock, Sleeper sleeper) {
        this.quota = quota;
        this.clock = clock;
        this.sleeper = sleeper;
    }

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
                // Ne jamais dormir au-delà de bulk-yield, sinon la garantie anti-famine se perd.
                capped = Math.min(capped, quota.getBulkYield().toMillis() - (now - start));
            }
            return Math.max(1, capped);
        } finally {
            lock.unlock();
        }
    }

    // Passé bulk-yield, la collecte cesse de compter les interactifs et devient comptée par eux : sans cette
    // réciprocité, un ouvrier seul perd indéfiniment la course au créneau. Les deux états s'excluent.
    private int reservedAhead(QuotaLane lane, long now, long start) {
        if (lane == QuotaLane.INTERACTIVE) {
            return starvedBulk;
        }
        return yieldElapsed(now, start) ? 0 : waitingInteractive;
    }

    private boolean yieldElapsed(long now, long start) {
        return now - start >= quota.getBulkYield().toMillis();
    }

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

    private int sustainedLimit(QuotaLane lane, long now) {
        int limit = effective(quota.getSustainedRequests());
        return lane == QuotaLane.INTERACTIVE ? limit : Math.max(1, limit - reserve(now));
    }

    // Garder un créneau : sans lui, la demande interactive suivante attendrait la fenêtre entière.
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

    // Espacer tant que la réserve est armée : une fenêtre prise d'un bloc ne libère plus rien pendant 100 s.
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

    // Pénalité globale à la clé, les deux voies confondues. Plafonnée contre un Retry-After aberrant.
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

    public double allowedPerMinute() {
        double burst = effective(quota.getBurstRequests()) * 60_000.0 / quota.getBurstWindow().toMillis();
        double sustained = (effective(quota.getSustainedRequests()) - configuredReserve())
                * 60_000.0 / quota.getSustainedWindow().toMillis();
        return Math.min(burst, sustained);
    }

    public Duration throttledFor() {
        lock.lock();
        try {
            long remaining = penalisedUntil - clock.millis();
            return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
        } finally {
            lock.unlock();
        }
    }

    public long granted(QuotaLane lane) {
        lock.lock();
        try {
            return granted.getOrDefault(lane, 0L);
        } finally {
            lock.unlock();
        }
    }

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
