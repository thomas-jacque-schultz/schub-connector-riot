package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "L'assise des statistiques d'un joueur.")
public record PlayerCoverage(
        String puuid,
        boolean tracked,
        long knownMatches,
        long analysedMatches,
        Instant firstPlayedAt,
        Instant lastPlayedAt,
        Instant firstSyncAt,
        Instant lastSyncAt
) {
}
