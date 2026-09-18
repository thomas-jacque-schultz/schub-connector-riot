package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Le point de reprise de l'historique d'un joueur : d'où repartir la prochaine fois.
 *
 * @param lastSyncStartedAt instant auquel la dernière synchronisation a <strong>commencé</strong>,
 *                          et non celui auquel elle s'est terminée. Une partie jouée pendant la
 *                          synchronisation serait sinon tombée dans l'angle mort entre les deux.
 * @param newestMatchAt     date de la partie connue la plus récente, quand un détail l'a
 *                          établie. Sert au diagnostic, pas à la reprise.
 */
@Document("riot_player_cursor")
public record PlayerHistoryCursor(
        @Id String puuid,
        Instant firstSyncAt,
        Instant lastSyncStartedAt,
        Instant newestMatchAt
) {
}
