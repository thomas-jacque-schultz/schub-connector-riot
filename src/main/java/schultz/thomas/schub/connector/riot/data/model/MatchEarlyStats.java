package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;

import java.util.Map;

// Dérivé de riot_match_timeline à la collecte : la lecture ne reparcourt pas 500 ko de brut par partie.
@Document("riot_match_early")
public record MatchEarlyStats(@Id String matchId, Map<String, MatchInsights.At15> byPuuid, Integer version) {

    // À incrémenter quand le calcul change : les parties déjà dérivées sont recalculées depuis la timeline stockée.
    public static final int CURRENT_VERSION = 2;

    public boolean current() {
        return version != null && version == CURRENT_VERSION;
    }
}
