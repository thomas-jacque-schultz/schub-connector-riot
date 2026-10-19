package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;
import java.util.Map;

public record RiotDataPurgeReport(
        Map<String, Long> removed,
        long total,
        Instant startedAt,
        Instant finishedAt
) {
}
