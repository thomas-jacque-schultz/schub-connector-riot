package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;

import java.time.Instant;

/**
 * La couche d'analyse : un document par (puuid, matchId), aplati.
 *
 * <p>Dérivée de {@link CachedMatch} et d'elle seule — jamais de Riot. Elle est donc jetable et
 * entièrement reconstructible, ce qui est la propriété à préserver : si ce modèle change, on
 * recalcule sans un seul appel.</p>
 *
 * @param side  100 ou 200, la valeur de Riot. Le côté est une variable de résultat à part
 *              entière, pas un détail d'affichage.
 * @param patch les deux premiers segments de {@code gameVersion}. Grouper sur la version
 *              complète ({@code 16.18.817.5716}) ne regrouperait rien : elle change à chaque
 *              build, pas à chaque patch.
 */
@Document("riot_participation")
@CompoundIndex(name = "puuid_startedAt", def = "{'puuid': 1, 'startedAt': -1}")
@CompoundIndex(name = "puuid_champion", def = "{'puuid': 1, 'championId': 1}")
@CompoundIndex(name = "puuid_queue", def = "{'puuid': 1, 'queueId': 1}")
public record MatchParticipation(
        @Id String id,
        @Indexed String puuid,
        @Indexed String matchId,
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
        int visionScore,
        boolean afk,
        Instant projectedAt
) {

    public static String idOf(String puuid, String matchId) {
        return puuid + "#" + matchId;
    }
}
