package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = """
        Bornes des indicateurs d'une population de joueurs à un poste : 5e et 95e percentiles de
        leurs moyennes sur la Faille. Une population locale — les comptes collectés — pas une
        référence mondiale.""")
public record MetricReference(
        @Schema(description = "Palier de la population, absent pour les adversaires rencontrés.") String tier,
        TeamPosition position,
        int population,
        int minimumGames,
        Map<String, Bound> bounds
) {

    public record Bound(double low, double high) {
    }
}
