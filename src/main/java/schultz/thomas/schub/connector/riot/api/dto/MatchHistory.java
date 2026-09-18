package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Les identifiants de parties connus pour un joueur depuis une date.
 *
 * <p><strong>Append-only</strong> : l'historique d'un joueur ne fait que s'allonger. On ne
 * redemande à Riot que ce qui est plus récent que le dernier relevé — moins le recouvrement.</p>
 *
 * @param matchIds  dédoublonnés, du plus récent au plus ancien.
 * @param syncedAt  date du dernier relevé auprès de Riot. Sert à l'appelant pour savoir s'il
 *                  regarde une donnée fraîche, et au connecteur comme point de reprise.
 * @param refreshed vrai si ce même appel a interrogé Riot, faux s'il a été servi du cache.
 */
@Schema(description = "Ids de parties d'un joueur depuis une date, servis du cache du connecteur.")
public record MatchHistory(
        String puuid,
        Instant since,
        List<String> matchIds,
        Instant syncedAt,
        boolean refreshed
) {
}
