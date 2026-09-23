package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Une liste d'identifiants de parties dont on veut le détail.")
public record MatchIdsRequest(
        @NotEmpty(message = "Au moins un identifiant de partie est attendu.")
        List<String> matchIds
) {
}
