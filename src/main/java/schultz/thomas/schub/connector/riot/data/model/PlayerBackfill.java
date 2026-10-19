package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

// Le rattrapage d'historique d'un compte lié (Schub#113). matchIds vide tant que les identifiants ne sont pas relevés.
@Document("riot_player_backfill")
public record PlayerBackfill(
        @Id String puuid,
        Instant since,
        Instant requestedAt,
        List<String> matchIds,
        Instant idsCollectedAt
) {
}
