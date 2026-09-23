package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.ParticipationBucket;
import schultz.thomas.schub.connector.riot.api.dto.PlayerCoverage;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatch;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatchPlayer;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatches;
import schultz.thomas.schub.connector.riot.api.dto.StatsGrouping;
import schultz.thomas.schub.connector.riot.api.dto.StatsScope;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerHistoryCursorRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerMatchRefRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ParticipationStatsService {

    public static final int SHARED_MATCHES_LIMIT_DEFAUT = 200;
    public static final int SHARED_MATCHES_LIMIT_MAX = 1000;

    private final MongoTemplate mongo;
    private final MatchParticipationRepository participations;
    private final PlayerMatchRefRepository matchRefs;
    private final PlayerHistoryCursorRepository cursors;

    public List<ParticipationBucket> aggregate(List<String> puuids, StatsGrouping groupBy, StatsScope scope,
                                               Instant since) {
        List<String> propres = propres(puuids);
        if (propres.isEmpty()) {
            return List.of();
        }
        GroupOperation group = Aggregation.group("puuid", "statsKey")
                .count().as("games")
                .sum(siVrai("win")).as("wins")
                .sum(siVrai("afk")).as("afkGames")
                .sum("kills").as("kills")
                .sum("deaths").as("deaths")
                .sum("assists").as("assists")
                .sum("minionsKilled").as("minionsKilled")
                .sum("goldEarned").as("goldEarned")
                .sum("damageToChampions").as("damageToChampions")
                .sum("damageTaken").as("damageTaken")
                .sum("visionScore").as("visionScore")
                .sum("durationSeconds").as("secondsPlayed")
                .min("startedAt").as("firstPlayedAt")
                .max("startedAt").as("lastPlayedAt");
        if (groupBy == StatsGrouping.CHAMPION) {
            group = group.first("championName").as("championName");
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(filtre(propres, since, groupBy, scope)),
                context -> new Document("$addFields", new Document("statsKey", cle(groupBy))),
                group);

        List<ParticipationBucket> buckets = new ArrayList<>();
        for (Document row : mongo.aggregate(aggregation, MatchParticipation.class, Document.class)) {
            Document id = row.get("_id", Document.class);
            buckets.add(new ParticipationBucket(
                    id.getString("puuid"),
                    groupBy,
                    Optional.ofNullable(id.getString("statsKey")).orElse(""),
                    row.getString("championName"),
                    entier(row, "games"),
                    entier(row, "wins"),
                    entier(row, "kills"),
                    entier(row, "deaths"),
                    entier(row, "assists"),
                    entier(row, "minionsKilled"),
                    entier(row, "goldEarned"),
                    entier(row, "damageToChampions"),
                    entier(row, "damageTaken"),
                    entier(row, "visionScore"),
                    entier(row, "afkGames"),
                    entier(row, "secondsPlayed"),
                    instant(row, "firstPlayedAt"),
                    instant(row, "lastPlayedAt")));
        }
        List<ParticipationBucket> rendus = groupBy == StatsGrouping.QUEUE ? parMode(buckets) : buckets;
        rendus.sort(Comparator.comparingLong(ParticipationBucket::games).reversed()
                .thenComparing(ParticipationBucket::key));
        return rendus;
    }

    // Regroupé sur queueId, pas sur le nom stocké : compléter QueueKind corrige ainsi les anciennes parties.
    private static List<ParticipationBucket> parMode(List<ParticipationBucket> buckets) {
        Map<String, ParticipationBucket> parCle = new LinkedHashMap<>();
        for (ParticipationBucket bucket : buckets) {
            String mode = QueueKind.fromQueueId(queueId(bucket.key())).name();
            parCle.merge(bucket.puuid() + "#" + mode,
                    new ParticipationBucket(bucket.puuid(), bucket.groupedBy(), mode, null,
                            bucket.games(), bucket.wins(), bucket.kills(), bucket.deaths(),
                            bucket.assists(), bucket.minionsKilled(), bucket.goldEarned(),
                            bucket.damageToChampions(), bucket.damageTaken(), bucket.visionScore(),
                            bucket.afkGames(),
                            bucket.secondsPlayed(), bucket.firstPlayedAt(), bucket.lastPlayedAt()),
                    ParticipationStatsService::additionne);
        }
        return new ArrayList<>(parCle.values());
    }

    private static ParticipationBucket additionne(ParticipationBucket a, ParticipationBucket b) {
        return new ParticipationBucket(a.puuid(), a.groupedBy(), a.key(), null,
                a.games() + b.games(),
                a.wins() + b.wins(),
                a.kills() + b.kills(),
                a.deaths() + b.deaths(),
                a.assists() + b.assists(),
                a.minionsKilled() + b.minionsKilled(),
                a.goldEarned() + b.goldEarned(),
                a.damageToChampions() + b.damageToChampions(),
                a.damageTaken() + b.damageTaken(),
                a.visionScore() + b.visionScore(),
                a.afkGames() + b.afkGames(),
                a.secondsPlayed() + b.secondsPlayed(),
                plusTot(a.firstPlayedAt(), b.firstPlayedAt()),
                plusTard(a.lastPlayedAt(), b.lastPlayedAt()));
    }

    private static Instant plusTot(Instant a, Instant b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return a.isBefore(b) ? a : b;
    }

    private static Instant plusTard(Instant a, Instant b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static int queueId(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public List<PlayerCoverage> coverage(List<String> puuids) {
        List<String> propres = propres(puuids);
        if (propres.isEmpty()) {
            return List.of();
        }
        Map<String, ParticipationBucket> bornes = new LinkedHashMap<>();
        aggregate(propres, StatsGrouping.OVERALL, StatsScope.ALL, null)
                .forEach(bucket -> bornes.put(bucket.puuid(), bucket));
        Map<String, PlayerHistoryCursor> parPuuid = new LinkedHashMap<>();
        cursors.findAllById(propres).forEach(cursor -> parPuuid.put(cursor.puuid(), cursor));

        List<PlayerCoverage> couverture = new ArrayList<>();
        for (String puuid : propres) {
            ParticipationBucket borne = bornes.get(puuid);
            PlayerHistoryCursor cursor = parPuuid.get(puuid);
            couverture.add(new PlayerCoverage(
                    puuid,
                    cursor != null,
                    matchRefs.countByPuuid(puuid),
                    borne == null ? 0L : borne.games(),
                    borne == null ? null : borne.firstPlayedAt(),
                    borne == null ? null : borne.lastPlayedAt(),
                    cursor == null ? null : cursor.firstSyncAt(),
                    cursor == null ? null : cursor.lastSyncStartedAt()));
        }
        return couverture;
    }

    public SharedMatches sharedMatches(List<String> puuids, int minimumPlayers, Instant since, Integer limit) {
        List<String> propres = propres(puuids);
        int seuil = Math.max(1, minimumPlayers);
        if (propres.size() < seuil) {
            return new SharedMatches(seuil, propres.size(), 0, false, List.of());
        }
        int borne = limit == null
                ? SHARED_MATCHES_LIMIT_DEFAUT
                : (int) Math.clamp(limit.longValue(), 1, SHARED_MATCHES_LIMIT_MAX);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(filtre(propres, since, null, StatsScope.ALL)),
                Aggregation.group("matchId")
                        .count().as("present")
                        .max("startedAt").as("startedAt"),
                Aggregation.match(Criteria.where("present").gte(seuil)),
                Aggregation.sort(Sort.Direction.DESC, "startedAt"));

        List<String> matchIds = new ArrayList<>();
        for (Document row : mongo.aggregate(aggregation, MatchParticipation.class, Document.class)) {
            matchIds.add(row.getString("_id"));
        }
        long total = matchIds.size();
        List<String> retenus = matchIds.size() > borne ? matchIds.subList(0, borne) : matchIds;
        return new SharedMatches(seuil, propres.size(), total, total > retenus.size(),
                assemble(retenus, Set.copyOf(propres)));
    }

    private List<SharedMatch> assemble(List<String> matchIds, Set<String> demandes) {
        if (matchIds.isEmpty()) {
            return List.of();
        }
        Map<String, List<MatchParticipation>> parMatch = new LinkedHashMap<>();
        matchIds.forEach(id -> parMatch.put(id, new ArrayList<>()));
        participations.findByMatchIdIn(matchIds)
                .forEach(row -> parMatch.get(row.matchId()).add(row));

        List<SharedMatch> rendus = new ArrayList<>();
        for (Map.Entry<String, List<MatchParticipation>> entree : parMatch.entrySet()) {
            List<MatchParticipation> tous = entree.getValue();
            List<MatchParticipation> rows = tous.stream().filter(row -> demandes.contains(row.puuid())).toList();
            if (rows.isEmpty()) {
                continue;
            }
            MatchParticipation premiere = rows.getFirst();
            boolean cotesSepares = rows.stream().mapToInt(MatchParticipation::side).distinct().count() > 1;
            rendus.add(new SharedMatch(
                    entree.getKey(),
                    premiere.startedAt(),
                    premiere.durationSeconds(),
                    premiere.queueId(),
                    premiere.queue(),
                    premiere.patch(),
                    premiere.complete(),
                    rows.size(),
                    cotesSepares,
                    cotesSepares ? null : premiere.win(),
                    tous.stream().map(row -> toPlayer(row, demandes.contains(row.puuid()))).toList()));
        }
        return rendus;
    }

    private static SharedMatchPlayer toPlayer(MatchParticipation row, boolean requested) {
        return new SharedMatchPlayer(row.puuid(), row.championId(), row.championName(),
                row.position(), row.win(), row.side(), row.kills(), row.deaths(), row.assists(),
                row.minionsKilled(), row.goldEarned(), row.damageToChampions(), row.damageTaken(),
                row.visionScore(), row.afk(), requested);
    }

    private static Criteria filtre(List<String> puuids, Instant since, StatsGrouping groupBy,
                                   StatsScope scope) {
        Criteria criteria = Criteria.where("puuid").in(puuids);
        if (scope == StatsScope.RIFT) {
            criteria = criteria.and("queueId").in(QueueKind.RIFT_QUEUE_IDS);
        }
        if (groupBy == StatsGrouping.POSITION) {
            criteria = criteria.and("position").nin(TeamPosition.UNKNOWN, null);
        }
        if (since != null) {
            criteria = criteria.and("startedAt").gte(Date.from(since));
        } else if (groupBy == StatsGrouping.MONTH) {
            criteria = criteria.and("startedAt").ne(null);
        }
        return criteria;
    }

    private static Document cle(StatsGrouping groupBy) {
        return switch (groupBy) {
            case OVERALL -> new Document("$literal", "");
            case CHAMPION -> new Document("$toString", "$championId");
            case QUEUE -> new Document("$toString", "$queueId");
            case SIDE -> new Document("$toString", "$side");
            case POSITION -> new Document("$toString", "$position");
            case PATCH -> new Document("$ifNull", List.of("$patch", ""));
            case MONTH -> new Document("$dateToString",
                    new Document("format", "%Y-%m").append("date", "$startedAt"));
        };
    }

    private static AggregationExpression siVrai(String champ) {
        return context -> new Document("$cond", List.of("$" + champ, 1, 0));
    }

    private static List<String> propres(List<String> puuids) {
        if (puuids == null) {
            return List.of();
        }
        return puuids.stream()
                .filter(puuid -> puuid != null && !puuid.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static long entier(Document row, String champ) {
        Object valeur = row.get(champ);
        return valeur instanceof Number nombre ? nombre.longValue() : 0L;
    }

    private static Instant instant(Document row, String champ) {
        Object valeur = row.get(champ);
        if (valeur instanceof Date date) {
            return date.toInstant();
        }
        return valeur instanceof Instant instant ? instant : null;
    }
}
