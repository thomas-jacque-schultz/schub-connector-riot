package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

// Des sommes, jamais de moyennes : l'appelant doit pouvoir recomposer les groupes.
public record ParticipationBucket(
        String puuid,
        StatsGrouping groupedBy,
        String key,
        String championName,
        long games,
        long wins,
        long kills,
        long deaths,
        long assists,
        long minionsKilled,
        long goldEarned,
        long damageToChampions,
        long damageTaken,
        long visionScore,
        long teamKills,
        long teamDeaths,
        long afkGames,
        long secondsPlayed,
        Instant firstPlayedAt,
        Instant lastPlayedAt,
        PerformanceSums performance
) {
}
