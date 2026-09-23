package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.data.model.CachedRanking;
import schultz.thomas.schub.connector.riot.data.model.MatchRankSnapshot;
import schultz.thomas.schub.connector.riot.data.model.RankSpan;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankHistory {

    private static final Set<QueueKind> FILES = Set.of(QueueKind.RANKED_SOLO, QueueKind.RANKED_FLEX);
    private static final int BACKFILL_LOT = 500;

    private final MongoTemplate mongo;

    public record Observation(String puuid, RankedStanding standing) {
    }

    public void record(String puuid, List<RankedStanding> standings) {
        recordAll(standings.stream().map(standing -> new Observation(puuid, standing)).toList());
    }

    // Même rang que la dernière plage : on la prolonge. Sinon on en ouvre une. Traitées dans l'ordre des relevés.
    public void recordAll(Collection<Observation> observations) {
        List<Observation> retenues = observations.stream()
                .filter(RankHistory::utilisable)
                .sorted(Comparator.comparing(observation -> observation.standing().observedAt()))
                .toList();
        if (retenues.isEmpty()) {
            return;
        }
        Map<String, RankSpan> dernieres = dernieres(retenues.stream().map(Observation::puuid).distinct().toList());
        BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.ORDERED, RankSpan.class);
        for (Observation observation : retenues) {
            RankedStanding standing = observation.standing();
            String cle = cle(observation.puuid(), standing.queue());
            RankSpan derniere = dernieres.get(cle);
            Instant vu = standing.observedAt();
            if (derniere != null && derniere.sameRank(standing)) {
                bulk.updateOne(Query.query(Criteria.where("_id").is(derniere.id())), new Update()
                        .min("firstSeenAt", vu)
                        .max("lastSeenAt", vu)
                        .set("leaguePoints", derniere.seenAt(vu, standing.leaguePoints()).leaguePoints()));
                dernieres.put(cle, derniere.seenAt(vu, standing.leaguePoints()));
            } else if (derniere == null || vu.isAfter(derniere.lastSeenAt())) {
                RankSpan ouverte = new RankSpan(new ObjectId().toHexString(), observation.puuid(), standing.queue(),
                        standing.tier(), standing.division(), standing.leaguePoints(), vu, vu);
                bulk.insert(ouverte);
                dernieres.put(cle, ouverte);
            }
        }
        bulk.execute();
    }

    // Solo d'abord, flex à défaut : le palier d'un joueur tel qu'on l'a vu en dernier.
    public Map<String, String> latestTiers(Collection<String> puuids) {
        Map<String, RankSpan> dernieres = dernieres(puuids);
        Map<String, String> paliers = new HashMap<>();
        for (String puuid : puuids) {
            RankSpan solo = dernieres.get(cle(puuid, QueueKind.RANKED_SOLO));
            RankSpan retenue = solo != null ? solo : dernieres.get(cle(puuid, QueueKind.RANKED_FLEX));
            if (retenue != null) {
                paliers.put(puuid, retenue.tier());
            }
        }
        return paliers;
    }

    // Le rang tenu à cet instant : la plage qui le couvre, sinon la plus proche à moins de la tolérance. Solo, flex à défaut.
    public Map<String, RankSpan> rankAt(Collection<String> puuids, Instant instant, Duration tolerance) {
        Query query = Query.query(Criteria.where("puuid").in(puuids).and("queue").in(FILES)
                .and("lastSeenAt").gte(instant.minus(tolerance))
                .and("firstSeenAt").lte(instant.plus(tolerance)));
        Map<String, RankSpan> retenues = new HashMap<>();
        for (RankSpan span : mongo.find(query, RankSpan.class)) {
            retenues.merge(span.puuid(), span, (a, b) -> meilleure(a, b, instant));
        }
        return retenues;
    }

    public Map<String, List<RankSpan>> spansOf(Collection<String> puuids) {
        Map<String, List<RankSpan>> parPuuid = new HashMap<>();
        mongo.find(Query.query(Criteria.where("puuid").in(puuids).and("queue").in(FILES)), RankSpan.class)
                .forEach(span -> parPuuid.computeIfAbsent(span.puuid(), cle -> new ArrayList<>()).add(span));
        return parPuuid;
    }

    // Même règle que rankAt : une plage qui couvre l'instant, à la tolérance près.
    public static boolean covers(List<RankSpan> spans, Instant instant, Duration tolerance) {
        return instant != null && spans.stream().anyMatch(span -> !span.lastSeenAt().isBefore(instant.minus(tolerance))
                && !span.firstSeenAt().isAfter(instant.plus(tolerance)));
    }

    private static RankSpan meilleure(RankSpan a, RankSpan b, Instant instant) {
        if (a.queue() != b.queue()) {
            return a.queue() == QueueKind.RANKED_SOLO ? a : b;
        }
        return distance(a, instant) <= distance(b, instant) ? a : b;
    }

    private static long distance(RankSpan span, Instant instant) {
        if (instant.isBefore(span.firstSeenAt())) {
            return Duration.between(instant, span.firstSeenAt()).toSeconds();
        }
        return instant.isAfter(span.lastSeenAt()) ? Duration.between(span.lastSeenAt(), instant).toSeconds() : 0;
    }

    public long backfillIfEmpty() {
        if (mongo.estimatedCount(RankSpan.class) > 0) {
            return 0;
        }
        List<Observation> observations = new ArrayList<>();
        for (CachedRanking ranking : mongo.findAll(CachedRanking.class)) {
            if (ranking.standings() != null) {
                ranking.standings().forEach(standing -> observations.add(new Observation(ranking.puuid(),
                        standing.observedAt() == null ? avec(standing, ranking.fetchedAt()) : standing)));
            }
        }
        for (MatchRankSnapshot snapshot : mongo.findAll(MatchRankSnapshot.class)) {
            snapshot.byPuuid().forEach((puuid, standings) -> standings.forEach(standing ->
                    observations.add(new Observation(puuid,
                            standing.observedAt() == null ? avec(standing, snapshot.observedAt()) : standing))));
        }
        observations.sort(Comparator.comparing(observation -> observation.standing().observedAt(),
                Comparator.nullsFirst(Comparator.naturalOrder())));
        for (int debut = 0; debut < observations.size(); debut += BACKFILL_LOT) {
            recordAll(observations.subList(debut, Math.min(debut + BACKFILL_LOT, observations.size())));
        }
        log.info("Historique des rangs amorcé depuis {} relevés existants.", observations.size());
        return observations.size();
    }

    private Map<String, RankSpan> dernieres(Collection<String> puuids) {
        Query query = Query.query(Criteria.where("puuid").in(puuids).and("queue").in(FILES))
                .with(Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        Map<String, RankSpan> dernieres = new HashMap<>();
        for (RankSpan span : mongo.find(query, RankSpan.class)) {
            dernieres.putIfAbsent(cle(span.puuid(), span.queue()), span);
        }
        return dernieres;
    }

    private static boolean utilisable(Observation observation) {
        RankedStanding standing = observation.standing();
        return observation.puuid() != null && !observation.puuid().isBlank()
                && standing != null && FILES.contains(standing.queue())
                && standing.tier() != null && standing.observedAt() != null;
    }

    private static RankedStanding avec(RankedStanding standing, Instant observedAt) {
        return new RankedStanding(standing.queue(), standing.riotQueueType(), standing.tier(), standing.division(),
                standing.leaguePoints(), standing.wins(), standing.losses(), standing.hotStreak(),
                standing.inactive(), observedAt);
    }

    private static String cle(String puuid, QueueKind queue) {
        return puuid + "#" + queue;
    }
}
