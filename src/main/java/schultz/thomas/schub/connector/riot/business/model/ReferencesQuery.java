package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.time.Instant;
import java.util.List;

public record ReferencesQuery(List<Player> players) {

    public record Player(String puuid, TeamPosition position, Instant since) {
    }
}
