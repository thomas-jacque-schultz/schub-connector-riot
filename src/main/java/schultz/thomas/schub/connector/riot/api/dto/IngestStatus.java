package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

/**
 * @param estimatedDrain « 4 300 en attente » ne dit rien, « prêt dans 1 h 25 » si.
 * @param throttledFor   pénalité 429 en cours, incluse dans l'écoulement.
 */
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
