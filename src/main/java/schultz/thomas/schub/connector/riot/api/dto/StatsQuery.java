package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

@Schema(description = "Ce qu'on veut agréger, et sur quel axe.")
public record StatsQuery(
        @NotEmpty List<String> puuids,
        @NotNull StatsGrouping groupBy,
        @Schema(description = "Absente : ALL.") StatsScope scope,
        @Schema(description = "Absente : tout l'historique connu.") Instant since
) {
}
