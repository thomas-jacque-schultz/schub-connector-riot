package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Ce qu'un joueur a fait dans une partie, sous forme normalisée.
 *
 * <p>Riot renvoie près de cent cinquante champs par participant — pings, missions, défis,
 * runes détaillées. On en garde ce qui sert à lire une partie ; le reste ferait grossir la base
 * pour rien. La timeline, qui pèse plusieurs mégaoctets, n'est jamais conservée.</p>
 *
 * @param afk {@code wasAfk} de Riot : utile pour qualifier une contre-performance, et pour ne
 *            pas compter comme « présent » un joueur qui ne l'était pas.
 */
@Schema(description = "Un joueur dans une partie, forme normalisée.")
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
        int visionScore,
        int summonerSpell1,
        int summonerSpell2,
        List<Integer> items,
        boolean afk
) {
}
