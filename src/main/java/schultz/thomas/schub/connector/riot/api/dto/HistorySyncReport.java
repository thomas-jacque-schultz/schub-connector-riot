package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

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
