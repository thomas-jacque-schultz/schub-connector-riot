package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.QueueKind;

import java.time.Instant;
import java.util.List;

public record MatchDetail(
        String matchId,
        int queueId,
        QueueKind queue,
        String gameVersion,
        String platform,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        boolean complete,
        List<MatchParticipant> participants,
        List<MatchTeamResult> teams
) {
}
