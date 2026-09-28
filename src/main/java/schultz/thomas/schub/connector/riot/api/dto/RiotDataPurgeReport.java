package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Effacement des données Riot : documents supprimés par collection.")
public record RiotDataPurgeReport(
        Map<String, Long> removed,
        long total,
        Instant startedAt,
        Instant finishedAt
) {
}
