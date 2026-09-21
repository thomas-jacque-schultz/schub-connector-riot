package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

/**
 * @param minimumPlayers combien de ces {@code puuids} doivent être présents pour qu'une partie
 *                       soit rendue. Le connecteur compte ; il ignore ce qu'est une équipe.
 */
@Schema(description = "Les parties où plusieurs de ces joueurs se retrouvent.")
public record SharedMatchesQuery(
        @NotEmpty List<String> puuids,
        @Positive int minimumPlayers,
        Instant since,
        Integer limit
) {
}
