package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Politique n°2 — <strong>append-only, incrémental</strong>.
 *
 * <p>Le lien « ce joueur a joué cette partie ». L'historique d'un joueur ne fait que
 * s'allonger : on ne redemande jamais à Riot une plage déjà relevée, seulement ce qui la suit.</p>
 *
 * <p>L'identifiant composite {@code puuid#matchId} <strong>est</strong> le dédoublonnage : le
 * recouvrement d'une heure renvoie forcément des ids déjà connus, et une clé unique les écarte
 * sans qu'il faille y penser à chaque écriture.</p>
 *
 * @param playedAt {@code null} tant que le détail de la partie n'a pas été récupéré — on ne
 *                 connaît alors que son id. Les lectures filtrées par date incluent ces
 *                 parties-là : les taire reviendrait à cacher précisément ce qui manque.
 */
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
