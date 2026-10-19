package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import schultz.thomas.schub.connector.riot.data.model.KnownAccountSource;
import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.time.Instant;
import java.util.List;

@Schema(description = "Un compte connu de notre index, avec de quoi le reconnaître et le dater.")
public record PlayerSuggestionDto(
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

    @Schema(description = "Un poste et le nombre de parties qu'on y a vu ce joueur.")
    public record PositionPlayed(TeamPosition position, long matches) {
    }
}
