package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Détails de parties, éventuellement partiels.")
public record MatchDetailsResponse(
        List<MatchDetail> matches,
        @Schema(description = "Ids dont le détail reste à récupérer : rappeler pour les obtenir.")
        List<String> pending,
        @Schema(description = "Ids refusés par Riot (partie inexistante ou purgée).")
        List<String> unavailable
) {
}
