package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("riot_game_version")
public record CachedGameVersion(@Id String id, String version, Instant fetchedAt) {

    public static final String CURRENT = "current";
}
