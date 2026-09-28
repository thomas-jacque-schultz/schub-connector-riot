package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Pause de toute la file. running : tâches prises avant la pause et pas encore terminées.")
public record IngestPauseStatus(boolean paused, Instant updatedAt, long running) {
}
