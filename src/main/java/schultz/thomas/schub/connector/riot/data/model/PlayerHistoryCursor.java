package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// lastSyncStartedAt : le début de la synchronisation, pas sa fin : une partie jouée pendant tomberait dans l'angle mort.
@Document("riot_player_cursor")
public record PlayerHistoryCursor(
        @Id String puuid,
        Instant firstSyncAt,
        Instant lastSyncStartedAt,
        Instant newestMatchAt
) {
}
