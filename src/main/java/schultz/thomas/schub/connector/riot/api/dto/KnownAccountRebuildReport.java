package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Reprojection des participations dans l'index des comptes connus.")
public record KnownAccountRebuildReport(
        int accountsRead,
        int accountsWritten,
        Instant startedAt,
        Instant finishedAt
) {
}
