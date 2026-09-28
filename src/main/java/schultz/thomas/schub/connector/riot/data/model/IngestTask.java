package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("riot_ingest_task")
@CompoundIndex(name = "state_priority", def = "{'state': 1, 'priority': -1}")
@CompoundIndex(name = "type_state", def = "{'type': 1, 'state': 1}")
public record IngestTask(
        @Id String id,
        IngestTaskType type,
        String key,
        @Indexed String puuid,
        @Indexed IngestTaskState state,
        long priority,
        Instant enqueuedAt,
        Instant notBefore,
        Instant leaseUntil,
        int attempts,
        String lastError
) {

    public static String idOf(IngestTaskType type, String key) {
        return type + ":" + key;
    }

    // EUW1_7990209944 : la séquence croît avec le temps à plateforme donnée.
    // Priorités négatives : la collecte de fond, toujours servie après les demandes des joueurs.
    public static final long BACKGROUND_PLAYER_PRIORITY = -1;
    private static final long BACKGROUND_OFFSET = Long.MIN_VALUE / 2;

    public static long backgroundPriority(long priority) {
        return BACKGROUND_OFFSET + priority;
    }

    // Les parties des graines du ladder passent avant celles de la collecte de fond : ce sont elles qui remplissent les
    // référentiels par palier, et leur nombre est borné. Toujours sous les tâches des pages et des comptes (-1).
    public static final long SAMPLING_OFFSET = Long.MIN_VALUE / 4;

    public static long samplingPriority(long priority) {
        return SAMPLING_OFFSET + priority;
    }

    // Aperçu d'un joueur recherché : servi avant l'échantillon et la collecte de fond, et sur la voie PRIORITY du quota,
    // que la collecte de fond laisse passer.
    private static final long PREVIEW_OFFSET = Long.MAX_VALUE / 2;

    public static long previewPriority(long sequence) {
        return PREVIEW_OFFSET + sequence;
    }

    // Joueurs actifs (compte lié ou place d'équipe) : leurs parties et l'enrichissement des parties d'équipe passent
    // avant tout le reste, joueurs recherchés compris.
    private static final long ACTIVE_OFFSET = Long.MAX_VALUE / 4 * 3;

    public static long activePriority(long sequence) {
        return ACTIVE_OFFSET + sequence;
    }

    // Le relevé d'un joueur recherché : sous les joueurs actifs, au-dessus des parties de tous les aperçus.
    public static final long SEARCHED_PLAYER_PRIORITY = ACTIVE_OFFSET - 1;

    // Seuil des tâches demandées par un joueur, actif ou recherché : les ouvriers réservés ne prennent rien en dessous.
    public static final long PRIORITY_FLOOR = PREVIEW_OFFSET;

    public boolean prioritaire() {
        return priority >= PRIORITY_FLOOR;
    }

    public boolean background() {
        return priority < 0;
    }

    public static long sequenceOf(String matchId) {
        int separator = matchId.lastIndexOf('_');
        if (separator < 0 || separator == matchId.length() - 1) {
            return 0;
        }
        try {
            return Long.parseLong(matchId.substring(separator + 1));
        } catch (NumberFormatException unparsable) {
            return 0;
        }
    }
}
