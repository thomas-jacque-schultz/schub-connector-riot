package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// Des sommes, jamais de moyennes : l'appelant doit pouvoir recomposer les groupes.
@Schema(description = "Sommes brutes d'un groupe de participations.")
public record ParticipationBucket(
        String puuid,
        StatsGrouping groupedBy,
        String key,
        @Schema(description = "CHAMPION seulement : le nom porté à l'époque.") String championName,
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
