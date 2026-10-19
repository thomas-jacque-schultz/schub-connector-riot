package schultz.thomas.schub.connector.riot.business.model;

import java.util.List;

public record SharedMatches(
        int minimumPlayers,
        int pool,
        long totalMatches,
        boolean truncated,
        List<SharedMatch> matches
) {
}
