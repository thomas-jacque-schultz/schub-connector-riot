package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Parties de l'échantillon par palier : la timeline brute n'est gardée que pour les parties d'équipe.
@Document("riot_timeline_digest")
public record CachedTimelineDigest(@Id String matchId, org.bson.Document raw, Instant fetchedAt) {
}
