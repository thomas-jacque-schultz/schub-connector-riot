package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Ne porte que les joueurs demandés ; les autres participants sont dans le détail de la partie.
 *
 * @param splitSides les joueurs demandés n'étaient pas tous du même côté : il n'y a pas de
 *                   résultat commun, et {@code win} est alors nul.
 */
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
