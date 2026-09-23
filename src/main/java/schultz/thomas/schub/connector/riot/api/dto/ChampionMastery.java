package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Maîtrise d'un champion par un joueur.")
public record ChampionMastery(
        int championId,
        String championName,
        int level,
        int points,
        Instant lastPlayedAt,
        Instant observedAt
) {
}
