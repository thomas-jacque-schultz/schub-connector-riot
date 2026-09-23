package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Ids de parties d'un joueur depuis une date, servis du cache du connecteur.")
public record MatchHistory(
        String puuid,
        Instant since,
        List<String> matchIds,
        Instant syncedAt,
        boolean ingestQueued
) {
}
