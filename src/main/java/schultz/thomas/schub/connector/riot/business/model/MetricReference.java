package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.util.Map;

public record MetricReference(
        String tier,
        TeamPosition position,
        int population,
        int minimumGames,
        Map<String, Bound> bounds
) {

    public record Bound(double low, double high) {
    }
}
