package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
