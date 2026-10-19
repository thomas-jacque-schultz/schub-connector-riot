package schultz.thomas.schub.connector.riot.business.model;

import java.util.List;
import java.util.Map;

public record RiotDataInventory(
        Map<String, Long> purged,
        List<String> kept
) {
}
