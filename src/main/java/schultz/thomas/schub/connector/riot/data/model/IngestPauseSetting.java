package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("riot_ingest_pause")
public record IngestPauseSetting(@Id String id, boolean paused, Instant updatedAt) {

    public static final String CURRENT = "current";
}
