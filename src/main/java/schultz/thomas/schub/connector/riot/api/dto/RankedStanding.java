package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rang et LP dans une file classée, avec la date du relevé.")
public record RankedStanding(
        QueueKind queue,
        @Schema(example = "RANKED_SOLO_5x5") String riotQueueType,
        @Schema(example = "CHALLENGER") String tier,
        @Schema(example = "I") String division,
        int leaguePoints,
        int wins,
        int losses,
        boolean hotStreak,
        boolean inactive,
        java.time.Instant observedAt
) {
}
