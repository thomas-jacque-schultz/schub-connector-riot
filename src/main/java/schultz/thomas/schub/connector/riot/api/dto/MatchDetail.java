package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

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
