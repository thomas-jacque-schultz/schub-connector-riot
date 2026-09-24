package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = """
        Répartition d'une métrique à un poste : une grille de quantiles par palier. La note se calcule chez
        l'appelant : une valeur se situe dans la grille de son palier par interpolation, et prend le palier dont
        la médiane est la plus proche quand `rankMedians` est présent.""")
public record ReferenceGrid(
        List<String> patches,
        @Schema(description = "GAME : une partie isolée. MEAN : la moyenne d'un joueur à au moins 10 parties.")
        String scope,
        @Schema(description = "Un poste, ou TEAM pour un camp entier.") String position,
        Instant computedAt,
        @Schema(description = "Les rangs des quantiles de chaque grille, de 0 à 1.") List<Double> percentiles,
        Map<String, Metric> metrics
) {

    public record Metric(
            @Schema(description = "HIGHER, LOWER (moins vaut mieux : inverser le percentile), NEUTRAL.") String polarity,
            @Schema(description = "Par palier : effectif et grille. Seulement le palier demandé, si demandé.")
            Map<String, Tier> tiers,
            @Schema(description = """
                    Médiane par partie de chaque palier assez fourni, du plus bas au plus haut. Absente quand la
                    métrique ne suit pas le rang : médianes qui ne montent pas d'un palier à l'autre, ou pas plus
                    que l'écart ordinaire au sein d'un palier.""")
            Map<String, Double> rankMedians,
            List<String> missingTiers
    ) {
    }

    public record Tier(long count, List<Double> values) {
    }
}
