package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

public record PlayerCoverage(
        String puuid,
        boolean tracked,
        long knownMatches,
        long analysedMatches,
        Instant firstPlayedAt,
        Instant lastPlayedAt,
        Instant firstSyncAt,
        Instant lastSyncAt
) {
}
