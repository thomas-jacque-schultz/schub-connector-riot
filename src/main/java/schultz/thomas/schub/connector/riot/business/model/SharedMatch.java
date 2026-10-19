package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.QueueKind;

import java.time.Instant;
import java.util.List;

public record SharedMatch(
        String matchId,
        Instant startedAt,
        long durationSeconds,
        int queueId,
        QueueKind queue,
        String patch,
        boolean complete,
        int presentPlayers,
        boolean splitSides,
        Boolean win,
        List<SharedMatchPlayer> players
) {
}
