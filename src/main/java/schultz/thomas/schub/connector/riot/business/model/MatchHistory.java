package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;
import java.util.List;

public record MatchHistory(
        String puuid,
        Instant since,
        List<String> matchIds,
        Instant syncedAt,
        boolean ingestQueued
) {
}
