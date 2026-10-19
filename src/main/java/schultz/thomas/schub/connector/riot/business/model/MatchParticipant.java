package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.util.List;

public record MatchParticipant(
        String puuid,
        String gameName,
        String tagLine,
        int championId,
        String championName,
        TeamPosition position,
        int teamId,
        boolean win,
        int kills,
        int deaths,
        int assists,
        int championLevel,
        int minionsKilled,
        int goldEarned,
        int damageToChampions,
        int damageTaken,
        int visionScore,
        int summonerSpell1,
        int summonerSpell2,
        List<Integer> items,
        boolean afk
) {
}
