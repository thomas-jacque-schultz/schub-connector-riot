package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** @param truncated la limite a coupé : il existe des parties plus anciennes au-dessus du seuil. */
@Schema(description = "Les parties communes trouvées, les plus récentes d'abord.")
public record SharedMatches(
        int minimumPlayers,
        int pool,
        long totalMatches,
        boolean truncated,
        List<SharedMatch> matches
) {
}
