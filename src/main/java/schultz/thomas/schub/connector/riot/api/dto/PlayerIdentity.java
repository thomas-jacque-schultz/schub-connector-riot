package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un joueur Riot. Le puuid est la seule clé stable ; le Riot ID peut changer.")
public record PlayerIdentity(
        @Schema(example = "c7E2IrpJgQxASKVyvRTWUJaJnmTwna7y1aIbc_C2aWwvO2KcC0nvLKCHkxrda6qCLWUCBovcaqa76w")
        String puuid,
        @Schema(example = "J1HUIV") String gameName,
        @Schema(example = "000") String tagLine
) {

    public String riotId() {
        return gameName + "#" + tagLine;
    }
}
