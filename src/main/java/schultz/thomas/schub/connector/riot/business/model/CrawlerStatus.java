package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

public record CrawlerStatus(
        boolean switchable,
        boolean enabled,
        boolean running,
        long knownAccounts,
        long trackedAccounts,
        long backgroundPending,
        long databaseBytes,
        long storageAlertBytes,
        boolean storageAlert,
        Instant lastRoundAt,
        int lastRoundAccounts
) {
}
