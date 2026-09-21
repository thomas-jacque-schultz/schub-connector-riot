package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

/**
 * Où en est la collecte <strong>d'un joueur</strong>.
 *
 * <p>{@code GET /ingest} dit l'état de la file entière : utile à l'exploitation, inutilisable
 * dans un écran de profil, qui ne parle que d'une personne.</p>
 *
 * @param estimatedReadyAt {@code null} quand il n'y a rien en file pour ce joueur — « prêt »
 *                         n'a alors pas de date, et renvoyer l'instant courant laisserait croire
 *                         qu'une collecte vient de finir.
 * @param queuedAhead      les appels qui passeront avant la dernière tâche de ce joueur, les
 *                         siennes comprises. L'ouvrier est unique : c'est ce nombre, et non le
 *                         sien propre, qui donne la date de fin.
 */
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
