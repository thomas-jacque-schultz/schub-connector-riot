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
        long killsDiffAt15,
        long earlyGames,
        long killsAssistsAt15,
        long soloKillsGames,
        long soloKills,
        long supportEarlyGames,
        long adcEarlyShare,
        long roams,
        long supportLateGames,
        long junglerLateShare
) {

    public PerformanceSums plus(PerformanceSums autre) {
        return new PerformanceSums(wardsPlaced + autre.wardsPlaced, wardsKilled + autre.wardsKilled,
                controlWardsPlaced + autre.controlWardsPlaced, timeDeadSeconds + autre.timeDeadSeconds,
                turretDamage + autre.turretDamage, turretTakedowns + autre.turretTakedowns,
                epicMonsterDamage + autre.epicMonsterDamage, teamDamageToChampions + autre.teamDamageToChampions,
                platesGames + autre.platesGames, platesDiff + autre.platesDiff, laningGames + autre.laningGames,
                goldDiffAt15 + autre.goldDiffAt15, csDiffAt15 + autre.csDiffAt15, xpDiffAt15 + autre.xpDiffAt15,
                killsDiffAt15 + autre.killsDiffAt15, earlyGames + autre.earlyGames,
                killsAssistsAt15 + autre.killsAssistsAt15, soloKillsGames + autre.soloKillsGames,
                soloKills + autre.soloKills, supportEarlyGames + autre.supportEarlyGames,
                adcEarlyShare + autre.adcEarlyShare, roams + autre.roams, supportLateGames + autre.supportLateGames,
                junglerLateShare + autre.junglerLateShare);
    }
}
