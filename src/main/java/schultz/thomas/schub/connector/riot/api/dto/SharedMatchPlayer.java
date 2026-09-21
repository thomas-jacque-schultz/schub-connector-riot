package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un des joueurs demandés, dans une partie où il était présent.")
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
        int visionScore,
        boolean afk
) {
}
