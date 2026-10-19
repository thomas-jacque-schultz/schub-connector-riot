package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

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
        boolean requested,
        String riotId
) {
}
