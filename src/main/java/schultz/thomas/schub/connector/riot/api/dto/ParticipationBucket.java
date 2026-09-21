package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Des sommes, jamais de moyenne : une moyenne rendue ici se ré-additionnerait faux dès que
 * l'appelant recompose deux groupes.
 *
 * @param key forme texte de la clé — id de champion, poste, {@link QueueKind}, patch,
 *            {@code yyyy-MM} ou côté. Vide pour {@link StatsGrouping#OVERALL}.
 *            <p>Pour {@link StatsGrouping#QUEUE}, c'est le <strong>mode de jeu</strong> et non le
 *            {@code queueId} : plusieurs identifiants désignent le même mode, et les compter à
 *            part n'apprend rien.</p>
 */
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
        long visionScore,
        long afkGames,
        long secondsPlayed,
        Instant firstPlayedAt,
        Instant lastPlayedAt
) {
}
