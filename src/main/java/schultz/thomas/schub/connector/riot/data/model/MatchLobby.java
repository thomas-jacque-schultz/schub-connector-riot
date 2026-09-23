package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Rang estimé d'une partie : celui de la graine qui l'a fait collecter. Le classé solo tient une partie à un palier près.
@Document("riot_match_lobby")
public record MatchLobby(@Id String matchId, String seedPuuid, String tier, String division, Instant observedAt) {
}
