package schultz.thomas.schub.connector.riot.business.services;

import java.time.Instant;
import java.util.List;

/** Le relevé d'identifiants seul, sans les détails : ce que l'ingestion consomme. */
public record IdSyncResult(
        String puuid,
        Instant queriedFrom,
        List<String> seen,
        int created,
        Instant startedAt
) {
}
