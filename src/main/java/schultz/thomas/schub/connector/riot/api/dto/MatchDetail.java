package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Une partie terminée, normalisée.
 *
 * <p><strong>Immuable</strong> : une partie finie ne change plus jamais. C'est ce qui autorise
 * le cache permanent, et c'est le cœur de l'exigence « ce qui a été pull un jour ne doit pas
 * l'être une deuxième fois ».</p>
 *
 * <p>Le connecteur ne dit pas si cette partie est « une partie d'équipe » : il ne sait pas ce
 * qu'est une équipe. Il rend les dix participants, le cœur croise.</p>
 *
 * @param queueId  la valeur brute de Riot, conservée telle quelle.
 * @param queue    sa lecture en clair. Aucune file n'est exclue à ce niveau.
 * @param complete {@code endOfGameResult} vaut {@code GameComplete}. Faux pour un remake ou une
 *                 partie interrompue — le cœur décide s'il la compte.
 */
@Schema(description = "Une partie terminée, normalisée. Immuable : jamais redemandée à Riot.")
public record MatchDetail(
        @Schema(example = "EUW1_7987650481") String matchId,
        @Schema(example = "420") int queueId,
        QueueKind queue,
        @Schema(example = "16.18.817.5716") String gameVersion,
        @Schema(example = "EUW1") String platform,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        boolean complete,
        List<MatchParticipant> participants,
        List<MatchTeamResult> teams
) {
}
