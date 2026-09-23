package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Sommes des métriques de partie de la projection v3. Les écarts à 15 min n'existent que pour les
        parties à timeline : leur dénominateur est laningGames, pas games.""")
public record PerformanceSums(
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
        long killsDiffAt15
) {

    public PerformanceSums plus(PerformanceSums autre) {
        return new PerformanceSums(wardsPlaced + autre.wardsPlaced, wardsKilled + autre.wardsKilled,
                controlWardsPlaced + autre.controlWardsPlaced, timeDeadSeconds + autre.timeDeadSeconds,
                turretDamage + autre.turretDamage, turretTakedowns + autre.turretTakedowns,
                epicMonsterDamage + autre.epicMonsterDamage, teamDamageToChampions + autre.teamDamageToChampions,
                platesGames + autre.platesGames, platesDiff + autre.platesDiff, laningGames + autre.laningGames,
                goldDiffAt15 + autre.goldDiffAt15, csDiffAt15 + autre.csDiffAt15, xpDiffAt15 + autre.xpDiffAt15,
                killsDiffAt15 + autre.killsDiffAt15);
    }
}
