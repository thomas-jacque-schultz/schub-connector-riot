package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Demande de détails en lot.
 *
 * <p>Un {@code POST} pour une lecture, à contre-courant de l'habitude : cent identifiants de
 * parties ne tiennent pas dans une URL, et le cœur en demande cent d'un coup quand il constitue
 * l'historique d'une équipe.</p>
 */
@Schema(description = "Une liste d'identifiants de parties dont on veut le détail.")
public record MatchIdsRequest(
        @NotEmpty(message = "Au moins un identifiant de partie est attendu.")
        List<String> matchIds
) {
}
