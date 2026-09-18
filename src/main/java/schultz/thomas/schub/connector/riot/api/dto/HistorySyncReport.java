package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Ce qu'une synchronisation d'historique a réellement fait.
 *
 * <p>Rendu plutôt que tu : une ingestion muette est une ingestion qu'on ne sait pas déboguer.
 * {@code detailsPending} est le nombre de parties dont l'id est connu mais dont le détail
 * n'a pas encore été récupéré — un premier remplissage est volontairement étalé sur plusieurs
 * appels pour ne pas faire pendre celui-ci le temps d'épuiser le quota.</p>
 *
 * @param queriedFrom la borne réellement envoyée à Riot : dernier relevé <em>moins</em> le
 *                    recouvrement. Exposée parce que c'est elle qu'on regarde quand une partie
 *                    manque.
 */
@Schema(description = "Compte rendu d'une synchronisation d'historique.")
public record HistorySyncReport(
        String puuid,
        Instant queriedFrom,
        int idsSeen,
        int idsNew,
        int detailsFetched,
        int detailsPending,
        Instant syncedAt
) {
}
