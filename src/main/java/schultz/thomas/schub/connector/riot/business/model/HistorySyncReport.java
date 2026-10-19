package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

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
