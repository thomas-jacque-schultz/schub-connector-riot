package schultz.thomas.schub.connector.riot.business.model;

import java.time.Duration;
import java.time.Instant;

public record PlayerIngestStatus(
        String puuid,
        long pending,
        long running,
        long failed,
        long queuedAhead,
        double callsPerMinute,
        Duration estimatedRemaining,
        Instant estimatedReadyAt,
        long priorityPending,
        Backfill backfill
) {

    // found : null tant que les identifiants ne sont pas relevés.
    public record Backfill(Instant since, Integer found, long collected) {
    }
}
