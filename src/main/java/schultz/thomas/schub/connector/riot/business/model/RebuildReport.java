package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

public record RebuildReport(
        int matchesRead,
        int participationsWritten,
        int unusableMatches,
        Instant startedAt,
        Instant finishedAt
) {
}
