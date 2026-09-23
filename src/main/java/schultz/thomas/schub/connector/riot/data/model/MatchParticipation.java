package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;

import java.time.Instant;

@Document("riot_participation")
@CompoundIndex(name = "puuid_startedAt", def = "{'puuid': 1, 'startedAt': -1}")
@CompoundIndex(name = "puuid_champion", def = "{'puuid': 1, 'championId': 1}")
@CompoundIndex(name = "puuid_queue", def = "{'puuid': 1, 'queueId': 1}")
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
        int teamKills,
        int teamDeaths,
        boolean afk,
        int projectionVersion,
        Instant projectedAt
) {

    // À incrémenter quand un champ dérivé du brut change : le démarrage reprojette alors tout.
    public static final int PROJECTION_VERSION = 2;

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
