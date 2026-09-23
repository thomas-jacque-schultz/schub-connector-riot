package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;

import java.util.Map;

// Dérivé de riot_match_timeline à la collecte : la lecture ne reparcourt pas 500 ko de brut par partie.
@Document("riot_match_early")
public record MatchEarlyStats(@Id String matchId, Map<String, MatchInsights.At15> byPuuid) {
}
