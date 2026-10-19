package schultz.thomas.schub.connector.riot.business.seo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.connector.riot.business.model.IndexablePlayer;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.QueueKind;
import schultz.thomas.schub.connector.riot.data.model.RankSpan;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Le plan du site ne liste que des profils fournis (Schub#91). Le décompte parcourt toutes les participations
// (7 s sur 4 M) : il est recalculé en tâche de fond, jamais pendant une requête.
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexablePlayers {

    private static final int LOT = 5_000;

    private static final List<String> APEX = List.of("MASTER", "GRANDMASTER", "CHALLENGER");

    private final MongoTemplate mongo;
    private final SeoCreatorService creators;
    private final RiotProperties properties;
    private final Clock clock;

    private volatile List<IndexablePlayer> snapshot = List.of();

    public List<IndexablePlayer> list(int limit) {
        return snapshot.stream().limit(limit).toList();
    }

    public void refresh() {
        RiotProperties.Seo config = properties.getSeo();
        Map<String, Long> parties = new LinkedHashMap<>();
        Aggregation comptage = Aggregation.newAggregation(
                Aggregation.group("puuid").count().as("games"),
                Aggregation.match(Criteria.where("games").gte(config.getMinGames())))
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
        for (Document ligne : mongo.aggregate(comptage, MatchParticipation.COLLECTION, Document.class)) {
            parties.put(ligne.getString("_id"), ((Number) ligne.get("games")).longValue());
        }

        Set<String> enTete = new HashSet<>(creators.puuids());
        enTete.addAll(mongo.findDistinct(Query.query(Criteria.where("queue").is(QueueKind.RANKED_SOLO)
                        .and("tier").in(APEX)
                        .and("lastSeenAt").gte(clock.instant().minus(config.getApexRecency()))),
                "puuid", RankSpan.class, String.class));

        List<KnownAccount> comptes = new ArrayList<>();
        List<String> puuids = new ArrayList<>(parties.keySet());
        for (int debut = 0; debut < puuids.size(); debut += LOT) {
            List<String> lot = puuids.subList(debut, Math.min(puuids.size(), debut + LOT));
            mongo.find(Query.query(Criteria.where("_id").in(lot)), KnownAccount.class).stream()
                    .filter(compte -> compte.gameName() != null && compte.tagLine() != null)
                    .forEach(comptes::add);
        }
        comptes.sort(Comparator.comparing((KnownAccount compte) -> !enTete.contains(compte.puuid()))
                .thenComparing(compte -> -parties.get(compte.puuid())));
        List<IndexablePlayer> joueurs = comptes.stream()
                .map(compte -> new IndexablePlayer(compte.gameName(), compte.tagLine(), enTete.contains(compte.puuid())))
                .toList();
        snapshot = joueurs;
        log.info("Plan du site : {} profils indexables, dont {} en tête", joueurs.size(),
                joueurs.stream().filter(IndexablePlayer::featured).count());
    }
}
