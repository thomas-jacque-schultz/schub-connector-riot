package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Une unité de travail d'ingestion, persistée.
 *
 * <p>La file vit en Mongo et non en mémoire : un premier remplissage dure des heures et sera
 * interrompu par un redémarrage. Une file mémoire perdrait le reste sans qu'on sache ce qui
 * manque — et c'est précisément ce qu'on ne peut pas détecter après coup.</p>
 *
 * @param id        {@code type:clé}. L'unicité de {@code _id} <strong>est</strong>
 *                  l'idempotence de l'empilement : aucun index à ajouter, aucune vérification
 *                  à ne pas oublier.
 * @param priority  décroissante. Pour une partie, la séquence du {@code matchId}, monotone dans
 *                  le temps à plateforme donnée : les parties récentes passent devant, parce que
 *                  celui qui vient de lier son compte veut cette semaine, pas 2019.
 * @param leaseUntil échéance du bail. Dépassée, la tâche redevient réclamable — c'est ce qui
 *                  rattrape un ouvrier tué en cours de route.
 */
@Document("riot_ingest_task")
@CompoundIndex(name = "claim", def = "{'state': 1, 'notBefore': 1, 'priority': -1}")
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

    /**
     * Les identifiants de Riot s'écrivent {@code EUW1_7990209944}, et la séquence croît avec le
     * temps à plateforme donnée. Elle sert donc d'horodatage avant d'avoir le détail — qu'on ne
     * possède justement pas encore au moment d'empiler.
     */
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
