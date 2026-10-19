package schultz.thomas.schub.connector.riot.business.model;

import java.time.Instant;

public record PatchStart(String patch, Instant startedAt) {
}
