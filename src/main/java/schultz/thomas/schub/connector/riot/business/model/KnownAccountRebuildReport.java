package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

public record KnownAccountRebuildReport(
        int accountsRead,
        int accountsWritten,
        Instant startedAt,
        Instant finishedAt
) {
}
