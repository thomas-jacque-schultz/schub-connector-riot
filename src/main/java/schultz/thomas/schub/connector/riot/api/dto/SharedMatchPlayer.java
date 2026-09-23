package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un joueur d'une partie partagée — les dix y sont.")
public record SharedMatchPlayer(
        String puuid,
        int championId,
        String championName,
        TeamPosition position,
        boolean win,
        int side,
        int kills,
        int deaths,
        int assists,
        int minionsKilled,
        int goldEarned,
        int damageToChampions,
        int damageTaken,
        int visionScore,
        boolean afk,
        @Schema(description = "Faux pour les autres joueurs de la partie, alliés comme adversaires.") boolean requested
) {
}
