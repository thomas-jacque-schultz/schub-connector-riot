package schultz.thomas.schub.connector.riot.api.dto;

import java.time.Instant;

public record PatchStartDto(String patch, Instant startedAt) {
}
