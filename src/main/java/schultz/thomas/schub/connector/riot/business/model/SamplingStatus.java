package schultz.thomas.schub.connector.riot.business.model;

import java.time.Duration;
import java.util.List;

public record SamplingStatus(Duration window, List<Tier> tiers, long lobbies) {

    public record Tier(String tier, long seeds, int target) {
    }
}
