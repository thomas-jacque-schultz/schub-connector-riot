package schultz.thomas.schub.connector.riot.business.services;

import java.time.Instant;
import java.util.List;

public record IdSyncResult(
        String puuid,
        Instant queriedFrom,
        List<String> seen,
        int created,
        Instant startedAt
) {
}
