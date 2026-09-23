package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;

import java.time.Instant;

@Document(MatchParticipation.COLLECTION)
@CompoundIndex(name = "puuid_startedAt", def = "{'puuid': 1, 'startedAt': -1}")
@CompoundIndex(name = "puuid_champion", def = "{'puuid': 1, 'championId': 1}")
@CompoundIndex(name = "puuid_queue", def = "{'puuid': 1, 'queueId': 1}")
@CompoundIndex(name = "patch_startedAt", def = "{'patch': 1, 'startedAt': 1}")
public record MatchParticipation(
        @Id String id,
        @Indexed String puuid,
        @Indexed String matchId,
        String gameName,
        String tagLine,
        @Indexed String searchName,
        int championId,
        String championName,
        TeamPosition position,
        boolean win,
        int side,
        long durationSeconds,
        int queueId,
        QueueKind queue,
        String gameVersion,
        String patch,
        String platform,
        Instant startedAt,
        boolean complete,
        int kills,
        int deaths,
        int assists,
        int minionsKilled,
        int goldEarned,
        int damageToChampions,
        int damageTaken,
        int visionScore,
        // Absents des lignes projetées avant la version 2, le temps que le démarrage les reprojette.
        Integer teamKills,
        Integer teamDeaths,
        boolean afk,
        Performance performance,
        Laning laning,
        RankAtGame rank,
        Integer projectionVersion,
        Instant projectedAt
) {

    // À incrémenter quand un champ dérivé du brut change : le démarrage reprojette alors tout.
    public static final int PROJECTION_VERSION = 3;

    public static final String COLLECTION = "riot_participation";

    // estimated : rang de la graine qui a fait collecter la partie, pas celui du joueur.
    public record RankAtGame(String tier, String division, boolean estimated) {
    }

    public record Performance(int wardsPlaced, int wardsKilled, int controlWardsPlaced, int timeDeadSeconds,
                              int turretDamage, int turretTakedowns, int epicMonsterDamage,
                              int teamDamageToChampions, Integer platesTaken) {
    }

    // Face à l'adversaire direct : l'autre joueur du même poste. Chiffres à 15 min absents sans timeline.
    public record Laning(String opponentPuuid, Integer platesDiff, Integer goldAt15, Integer csAt15, Integer xpAt15,
                         Integer killsAt15, Integer deathsAt15, Integer goldDiffAt15, Integer csDiffAt15,
                         Integer xpDiffAt15, Integer killsDiffAt15) {
    }

    public static String idOf(String puuid, String matchId) {
        return puuid + "#" + matchId;
    }

    public String riotId() {
        if (gameName == null || gameName.isBlank()) {
            return null;
        }
        return tagLine == null || tagLine.isBlank() ? gameName : gameName + "#" + tagLine;
    }
}
