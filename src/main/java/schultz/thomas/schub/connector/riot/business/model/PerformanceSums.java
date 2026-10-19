package schultz.thomas.schub.connector.riot.business.model;

public record PerformanceSums(
        long wardsPlaced,
        long wardsKilled,
        long controlWardsPlaced,
        long timeDeadSeconds,
        long turretDamage,
        long turretTakedowns,
        long epicMonsterDamage,
        long teamDamageToChampions,
        long platesGames,
        long platesDiff,
        long laningGames,
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
