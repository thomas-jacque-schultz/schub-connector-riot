package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

public record IngestPauseStatus(boolean paused, Instant updatedAt, long running) {
}
