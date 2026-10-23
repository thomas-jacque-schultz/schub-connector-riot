package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Sommes des métriques de partie de la projection v4. Les écarts à 15 min n'existent que pour les
        parties à timeline : leur dénominateur est laningGames, pas games.""")
public record PerformanceSumsDto(
        long wardsPlaced,
        long wardsKilled,
        long controlWardsPlaced,
        long timeDeadSeconds,
        long turretDamage,
        long turretTakedowns,
        long epicMonsterDamage,
        long teamDamageToChampions,
        @Schema(description = "Parties où l'écart de plaques est connu.") long platesGames,
        long platesDiff,
        @Schema(description = "Parties où les écarts à 15 min sont connus.") long laningGames,
        long goldDiffAt15,
        long csDiffAt15,
        long xpDiffAt15,
        long killsDiffAt15,
        @Schema(description = "Parties où les kills et assists à 15 min sont connus (timeline, projection v4).")
        long earlyGames,
        long killsAssistsAt15,
        @Schema(description = "Parties où Riot a servi les solo kills (challenges, projection v4).") long soloKillsGames,
        long soloKills
) {
}
