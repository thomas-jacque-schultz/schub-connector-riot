package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

@Schema(description = "État de la file d'ingestion et temps d'écoulement estimé.")
public record IngestStatus(
        long pending,
        long running,
        long failed,
        @Schema(example = "49.0") double callsPerMinute,
        Duration estimatedDrain,
        Instant estimatedReadyAt,
        Duration throttledFor
) {
}
