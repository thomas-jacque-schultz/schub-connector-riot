package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = """
        Moyennes des joueurs d'un champion (au moins 5 parties sur la fenêtre), dans un groupe de paliers :
        IRON_BRONZE, SILVER_GOLD, PLATINUM_EMERALD, DIAMOND, MASTER_PLUS. Une métrique absente : moins de
        30 joueurs dans le groupe, l'appelant se rabat sur le référentiel du poste.""")
public record ChampionReferenceGrid(
        int championId,
        String group,
        List<String> patches,
        Instant computedAt,
        List<Double> percentiles,
        Map<String, Metric> metrics
) {

    public record Metric(String polarity, long count, List<Double> values) {
    }
}
