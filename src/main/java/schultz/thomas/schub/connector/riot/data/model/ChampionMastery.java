package schultz.thomas.schub.connector.riot.data.model;

import java.time.Instant;

public record ChampionMastery(
        int championId,
        String championName,
        int level,
        int points,
        Instant lastPlayedAt,
        Instant observedAt
) {
}
