package schultz.thomas.schub.connector.riot.business.model;

import java.util.List;

public record MatchDetailsResponse(
        List<MatchDetail> matches,
        List<String> pending,
        List<String> unavailable
) {
}
