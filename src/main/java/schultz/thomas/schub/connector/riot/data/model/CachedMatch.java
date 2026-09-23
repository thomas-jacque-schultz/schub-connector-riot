package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;

import java.time.Instant;

// JSON intégral : Riot ne garde qu'environ mille parties par joueur, ce qui n'est pas capté est perdu.
@org.springframework.data.mongodb.core.mapping.Document("riot_match")
public record CachedMatch(@Id String matchId, org.bson.Document raw, Instant fetchedAt) {
}
