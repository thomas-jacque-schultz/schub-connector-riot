package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

@Schema(description = "Avancement de la collecte pour un joueur.")
public record PlayerIngestStatus(
        String puuid,
        long pending,
        long running,
        long failed,
        long queuedAhead,
        @Schema(example = "49.0") double callsPerMinute,
        Duration estimatedRemaining,
        Instant estimatedReadyAt
) {
}
