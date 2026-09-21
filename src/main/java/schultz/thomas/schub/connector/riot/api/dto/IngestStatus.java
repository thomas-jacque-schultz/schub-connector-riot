package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

/**
 * L'état de l'ingestion.
 *
 * @param estimatedDrain le seul chiffre qui réponde à « quand mes données seront-elles prêtes ? ».
 *                       « 4 300 en attente » ne dit rien ; « prêt dans 1 h 25 » si.
 * @param throttledFor   pénalité 429 en cours. Elle s'ajoute à l'écoulement, sinon l'estimation
 *                       serait optimiste exactement au moment où elle compte.
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
