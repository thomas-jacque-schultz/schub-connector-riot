package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ReferenceGrid(
        List<String> patches,
        String scope,
        String position,
        Instant computedAt,
        List<Double> percentiles,
        Map<String, Metric> metrics,
        Map<String, Distribution> distributions
) {

    public record Distribution(double step, long count, List<Long> counts) {
    }

    public record Metric(
            String polarity,
            Map<String, Tier> tiers,
            Map<String, Double> rankMeans,
            List<String> missingTiers
    ) {
    }

    public record Tier(long count, List<Double> values) {
    }
}
