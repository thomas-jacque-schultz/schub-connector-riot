package schultz.thomas.schub.connector.riot.business.model;

import java.time.Duration;
import java.time.Instant;

public record IngestStatus(
        long pending,
        long running,
        long failed,
        double callsPerMinute,
        Duration estimatedDrain,
        Instant estimatedReadyAt,
        Duration throttledFor
) {
}
