package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Le classement d'un joueur dans une file, à l'instant où on l'a relevé.
 *
 * <p><strong>Volatil</strong> : c'est la donnée la plus périssable du connecteur. Afficher un
 * LP d'hier serait un bug visible, d'où un TTL d'une heure là où une partie terminée est gardée
 * pour toujours.</p>
 *
 * @param queue       file classée concernée ({@code RANKED_SOLO} ou {@code RANKED_FLEX}).
 * @param tier        {@code IRON} … {@code CHALLENGER}.
 * @param division    {@code I} à {@code IV}. Vide au-dessus de master.
 * @param leaguePoints les LP.
 * @param observedAt  quand ce relevé a été fait. Rendu à l'appelant pour qu'il sache ce qu'il
 *                    affiche : une donnée de cache qui tait son âge est une donnée fausse.
 */
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
