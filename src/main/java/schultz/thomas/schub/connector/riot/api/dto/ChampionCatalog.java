package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Catalogue des champions figé à une version Data Dragon.")
public record ChampionCatalog(
        @Schema(example = "16.18.1") String version,
        @Schema(example = "fr_FR") String locale,
        List<ChampionCard> champions
) {
}
