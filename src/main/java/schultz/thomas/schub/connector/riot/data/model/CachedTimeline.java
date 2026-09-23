package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Collectée pour les parties d'équipe seulement : environ 500 ko par partie.
@Document("riot_match_timeline")
public record CachedTimeline(@Id String matchId, org.bson.Document raw, Instant fetchedAt) {
}
