package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un joueur, identifié par ce qui ne change pas.
 *
 * @param puuid    la <strong>seule</strong> clé stable. Un joueur peut changer de Riot ID ;
 *                 ne jamais stocker un pseudo comme identifiant.
 * @param gameName partie gauche du Riot ID, telle que Riot la renvoie aujourd'hui.
 * @param tagLine  partie droite du Riot ID, sans le {@code #}.
 */
@Schema(description = "Un joueur Riot. Le puuid est la seule clé stable ; le Riot ID peut changer.")
public record PlayerIdentity(
        @Schema(example = "c7E2IrpJgQxASKVyvRTWUJaJnmTwna7y1aIbc_C2aWwvO2KcC0nvLKCHkxrda6qCLWUCBovcaqa76w")
        String puuid,
        @Schema(example = "J1HUIV") String gameName,
        @Schema(example = "000") String tagLine
) {

    /** Le Riot ID tel qu'on l'écrit : {@code Pseudo#TAG}. */
    public String riotId() {
        return gameName + "#" + tagLine;
    }
}
