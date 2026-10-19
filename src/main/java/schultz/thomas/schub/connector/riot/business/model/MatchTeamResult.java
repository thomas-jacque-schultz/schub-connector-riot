package schultz.thomas.schub.connector.riot.business.model;

import java.util.List;
import java.util.Map;

public record MatchTeamResult(
        int teamId,
        boolean win,
        List<Integer> bannedChampionIds,
        Map<String, Integer> objectives
) {
}
