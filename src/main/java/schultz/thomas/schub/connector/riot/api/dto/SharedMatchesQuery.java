package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

@Schema(description = "Les parties où plusieurs de ces joueurs se retrouvent.")
public record SharedMatchesQuery(
        @NotEmpty List<String> puuids,
        @Positive int minimumPlayers,
        Instant since,
        Integer limit,
        @Schema(description = "Empile timeline et rangs des parties rendues qui ne les ont pas encore.") Boolean enrich
) {
}
