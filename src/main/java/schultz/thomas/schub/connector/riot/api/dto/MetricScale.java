package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = """
        Bornes des indicateurs, tirées des moyennes par joueur sur la Faille : 5e et 95e
        percentiles des joueurs croisés dans les parties collectées. Une population locale,
        pas une référence mondiale.""")
public record MetricScale(
        Instant computedAt,
        int population,
        int minimumGames,
        @Schema(description = "Du plus récent au plus ancien.") List<String> recentPatches,
        Map<String, Bound> bounds
) {

    public record Bound(double low, double high) {
    }
}
