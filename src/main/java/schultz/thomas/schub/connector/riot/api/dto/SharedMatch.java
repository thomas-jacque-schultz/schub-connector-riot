package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Une partie commune, avec les joueurs demandés qui y étaient.")
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
