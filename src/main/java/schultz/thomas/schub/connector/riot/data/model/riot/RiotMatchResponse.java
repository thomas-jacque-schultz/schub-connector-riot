package schultz.thomas.schub.connector.riot.data.model.riot;

import java.util.List;
import java.util.Map;

/**
 * Forme brute de {@code match-v5} — route <strong>régionale</strong>.
 *
 * <p>Riot rend près de 80 ko par partie et cent cinquante champs par participant. On ne
 * déclare ici que ce qu'on garde : Jackson ignore le reste, et ce qui n'est pas déclaré ne
 * finit pas en base.</p>
 */
public record RiotMatchResponse(Metadata metadata, Info info) {

    public record Metadata(String dataVersion, String matchId, List<String> participants) {
    }

    /**
     * @param gameDuration     en secondes depuis le patch 11.20, en millisecondes avant.
     *                         {@code gameEndTimestamp} sert à trancher : il n'existe que dans
     *                         le cas « secondes ».
     * @param endOfGameResult  {@code GameComplete}, ou autre chose pour un remake.
     */
    public record Info(
            long gameId,
            int queueId,
            String gameMode,
            String gameType,
            String gameVersion,
            String platformId,
            long gameCreation,
            long gameStartTimestamp,
            long gameEndTimestamp,
            long gameDuration,
            String endOfGameResult,
            List<Participant> participants,
            List<Team> teams
    ) {
    }

    public record Participant(
            String puuid,
            String riotIdGameName,
            String riotIdTagline,
            int championId,
            String championName,
            int teamId,
            String teamPosition,
            String individualPosition,
            boolean win,
            int kills,
            int deaths,
            int assists,
            int champLevel,
            int totalMinionsKilled,
            int neutralMinionsKilled,
            int goldEarned,
            int totalDamageDealtToChampions,
            int visionScore,
            int summoner1Id,
            int summoner2Id,
            int item0,
            int item1,
            int item2,
            int item3,
            int item4,
            int item5,
            int item6,
            boolean wasAfk
    ) {

        public List<Integer> items() {
            return List.of(item0, item1, item2, item3, item4, item5, item6);
        }
    }

    public record Team(int teamId, boolean win, List<Ban> bans, Map<String, Objective> objectives) {
    }

    /** {@code championId} vaut -1 quand personne n'a banni à ce tour. */
    public record Ban(int championId, int pickTurn) {
    }

    public record Objective(boolean first, int kills) {
    }
}
