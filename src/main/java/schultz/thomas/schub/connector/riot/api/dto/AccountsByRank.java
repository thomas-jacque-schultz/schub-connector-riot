package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Comptes relevés et graines du ladder, par rang solo/duo, de UNRANKED à CHALLENGER.")
public record AccountsByRank(List<Row> rows) {

    public record Row(
            @Schema(description = "UNRANKED quand aucun rang solo/duo n'a été relevé.") String tier,
            @Schema(description = "Comptes dont l'historique a été relevé, à leur dernier rang.") long tracked,
            @Schema(description = "Graines tirées dans les classements, au rang du tirage.") long seeds) {
    }
}
