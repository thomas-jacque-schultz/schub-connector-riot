package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

public record PlayerReferences(
        String puuid,
        TeamPosition position,
        String tier,
        MetricReference met
) {
}
