package schultz.thomas.schub.connector.riot.business.model;

import java.util.Map;

public record TimelineHabits(
        int games,
        Map<String, Double> means,
        Map<String, Integer> counts
) {
}
