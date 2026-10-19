package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.util.Map;

public record MatchPlayerMetrics(
        String puuid,
        int side,
        TeamPosition position,
        int championId,
        String tier,
        boolean tierEstimated,
        Map<String, Double> values
) {
}
