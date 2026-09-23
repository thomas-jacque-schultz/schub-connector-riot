package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("riot_player_match")
@CompoundIndex(name = "puuid_playedAt", def = "{'puuid': 1, 'playedAt': -1}")
public record PlayerMatchRef(
        @Id String id,
        @Indexed String puuid,
        String matchId,
        Instant playedAt,
        Instant discoveredAt
) {

    public static String idOf(String puuid, String matchId) {
        return puuid + "#" + matchId;
    }

    public PlayerMatchRef playedAt(Instant instant) {
        return new PlayerMatchRef(id, puuid, matchId, instant, discoveredAt);
    }
}
