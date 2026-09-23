package schultz.thomas.schub.connector.riot.api.dto;

import java.time.Instant;

public record PatchStart(String patch, Instant startedAt) {
}
