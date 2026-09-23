package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = """
        Répartition d'une métrique à un poste : une grille de quantiles par palier, et celle du ladder
        entier (paliers pondérés par leur part réelle). La note se calcule chez l'appelant : une valeur
        se situe dans la grille par interpolation, et le percentile du ladder se lit sur `levels`.""")
public record ReferenceGrid(
        List<String> patches,
        @Schema(description = "GAME : une partie isolée. MEAN : la moyenne d'un joueur à au moins 10 parties.")
        String scope,
        @Schema(description = "Un poste, ou TEAM pour un camp entier.") String position,
        Instant computedAt,
        @Schema(description = "La répartition du ladder utilisée, et sa date.") String distribution,
        @Schema(description = "Les rangs des quantiles de chaque grille, de 0 à 1.") List<Double> percentiles,
        @Schema(description = "Percentile à partir duquel commence chaque palier, du plus bas au plus haut.")
        List<Level> levels,
        Map<String, Metric> metrics
) {

    public record Level(String tier, double fromPercentile) {
    }

    public record Metric(
            @Schema(description = "HIGHER, LOWER (moins vaut mieux : inverser le percentile), NEUTRAL.") String polarity,
            @Schema(description = "Par palier : effectif et grille. Seulement le palier demandé, si demandé.")
            Map<String, Tier> tiers,
            @Schema(description = "Absente tant qu'un palier manque ou reste sous l'effectif minimum.") List<Double> ladder,
            List<String> missingTiers
    ) {
    }

    public record Tier(long count, List<Double> values) {
    }
}
