package schultz.thomas.schub.connector.riot.api.dto;

import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;

import java.time.Instant;

public record IngestTaskDto(
        String id,
        IngestTaskType type,
        String key,
        String puuid,
        IngestTaskState state,
        long priority,
        Instant enqueuedAt,
        Instant notBefore,
        Instant leaseUntil,
        int attempts,
        String lastError
) {
}
