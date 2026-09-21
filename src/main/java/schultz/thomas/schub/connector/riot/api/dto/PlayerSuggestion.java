package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Un compte que notre index fait connaître.
 *
 * <p>Deux dates, et elles ne disent pas la même chose. {@code lastPlayedAt} est la dernière
 * partie où on a croisé ce joueur — nulle pour un compte confirmé par Riot et jamais rencontré.
 * {@code observedAt} date l'identité elle-même : c'est elle qui dit si le Riot ID affiché est
 * frais ou s'il mérite d'être revérifié.</p>
 *
 * @param gameName le Riot ID tel qu'il était à {@code observedAt}. Il peut avoir changé depuis :
 *                 le {@code puuid} est la seule clé stable.
 */
@Schema(description = "Un compte connu de notre index, avec de quoi le reconnaître et le dater.")
public record PlayerSuggestion(
        String puuid,
        String gameName,
        String tagLine,
        String riotId,
        long matchCount,
        List<PositionPlayed> positions,
        Instant lastPlayedAt,
        Instant observedAt,
        KnownAccountSource source
) {

    @Schema(description = "Un poste et le nombre de parties qu'on y a vu ce joueur.")
    public record PositionPlayed(TeamPosition position, long matches) {
    }
}
