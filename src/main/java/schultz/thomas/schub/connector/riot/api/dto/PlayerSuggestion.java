package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Un compte que nos parties collectées font connaître.
 *
 * <p>Les chiffres portent sur <strong>nos</strong> données, pas sur la carrière du joueur :
 * {@code matchCount} est le nombre de parties où on l'a croisé, {@code lastPlayedAt} la plus
 * récente d'entre elles. C'est ce qui permet à quelqu'un de reconnaître son propre compte dans
 * une liste d'homonymes, et rien de plus ne doit y être lu.</p>
 *
 * @param gameName le Riot ID tel qu'il était lors de la partie la plus récente qu'on connaisse.
 *                 Il peut avoir changé depuis : le {@code puuid} est la seule clé stable.
 */
@Schema(description = "Un compte connu de nos participations, avec de quoi le reconnaître.")
public record PlayerSuggestion(
        String puuid,
        String gameName,
        String tagLine,
        String riotId,
        long matchCount,
        List<PositionPlayed> positions,
        Instant lastPlayedAt
) {

    @Schema(description = "Un poste et le nombre de parties qu'on y a vu ce joueur.")
    public record PositionPlayed(TeamPosition position, long matches) {
    }
}
