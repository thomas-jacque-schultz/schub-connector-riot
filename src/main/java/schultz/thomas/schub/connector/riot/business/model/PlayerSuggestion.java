package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.KnownAccountSource;
import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.time.Instant;
import java.util.List;

public record PlayerSuggestion(
        String puuid,
        String gameName,
        String tagLine,
        String riotId,
        long matchCount,
        List<PositionPlayed> positions,
        Instant lastPlayedAt,
        Instant observedAt,
        KnownAccountSource source
) {

    public record PositionPlayed(TeamPosition position, long matches) {
    }
}
