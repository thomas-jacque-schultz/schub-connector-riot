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
import schultz.thomas.schub.connector.riot.api.dto.SharedMatch;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatchPlayer;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatches;
import schultz.thomas.schub.connector.riot.api.dto.StatsGrouping;
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

/**
 * Les comptes tirés de {@code riot_participation}, et rien d'autre.
 *
 * <p>Le calcul est ici parce que la donnée est ici : sortir dix mille participations sur HTTP
 * pour les compter ailleurs coûterait le transfert à chaque affichage d'écran. Ce qui reste au
 * cœur, c'est ce que ces comptes veulent dire.</p>
 *
 * <p>Tous les regroupements attaquent {@code puuid} en premier : c'est l'index
 * {@code puuid_startedAt} qui porte le filtre, et aucune agrégation ne balaie la collection.</p>
 */
@Service
@RequiredArgsConstructor
public class ParticipationStatsService {

    public static final int SHARED_MATCHES_LIMIT_DEFAUT = 200;
    public static final int SHARED_MATCHES_LIMIT_MAX = 1000;

    private final MongoTemplate mongo;
    private final MatchParticipationRepository participations;
    private final PlayerMatchRefRepository matchRefs;
    private final PlayerHistoryCursorRepository cursors;

    public List<ParticipationBucket> aggregate(List<String> puuids, StatsGrouping groupBy, Instant since) {
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
                .sum("visionScore").as("visionScore")
                .sum("durationSeconds").as("secondsPlayed")
                .min("startedAt").as("firstPlayedAt")
                .max("startedAt").as("lastPlayedAt");
        if (groupBy == StatsGrouping.CHAMPION) {
            group = group.first("championName").as("championName");
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(filtre(propres, since, groupBy)),
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
                    entier(row, "visionScore"),
                    entier(row, "afkGames"),
                    entier(row, "secondsPlayed"),
                    instant(row, "firstPlayedAt"),
                    instant(row, "lastPlayedAt")));
        }
        buckets.sort(Comparator.comparingLong(ParticipationBucket::games).reversed()
                .thenComparing(ParticipationBucket::key));
        return buckets;
    }

    public List<PlayerCoverage> coverage(List<String> puuids) {
        List<String> propres = propres(puuids);
        if (propres.isEmpty()) {
            return List.of();
        }
        Map<String, ParticipationBucket> bornes = new LinkedHashMap<>();
        aggregate(propres, StatsGrouping.OVERALL, null)
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
                Aggregation.match(filtre(propres, since, null)),
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

    /**
     * Le {@code $group} ci-dessus compte des participations, pas des joueurs distincts : deux
     * documents pour le même puuid dans la même partie le feraient compter deux fois. L'identité
     * {@code puuid#matchId} l'interdit, et c'est elle qui rend ce compte exact.
     */
    private List<SharedMatch> assemble(List<String> matchIds, Set<String> demandes) {
        if (matchIds.isEmpty()) {
            return List.of();
        }
        Map<String, List<MatchParticipation>> parMatch = new LinkedHashMap<>();
        matchIds.forEach(id -> parMatch.put(id, new ArrayList<>()));
        participations.findByMatchIdIn(matchIds).stream()
                .filter(row -> demandes.contains(row.puuid()))
                .forEach(row -> parMatch.get(row.matchId()).add(row));

        List<SharedMatch> rendus = new ArrayList<>();
        for (Map.Entry<String, List<MatchParticipation>> entree : parMatch.entrySet()) {
            List<MatchParticipation> rows = entree.getValue();
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
                    rows.stream().map(ParticipationStatsService::toPlayer).toList()));
        }
        return rendus;
    }

    private static SharedMatchPlayer toPlayer(MatchParticipation row) {
        return new SharedMatchPlayer(row.puuid(), row.championId(), row.championName(),
                row.position(), row.win(), row.side(), row.kills(), row.deaths(), row.assists(),
                row.minionsKilled(), row.goldEarned(), row.damageToChampions(), row.visionScore(),
                row.afk());
    }

    private static Criteria filtre(List<String> puuids, Instant since, StatsGrouping groupBy) {
        Criteria criteria = Criteria.where("puuid").in(puuids);
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
            case POSITION -> new Document("$ifNull", List.of("$position", "UNKNOWN"));
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
