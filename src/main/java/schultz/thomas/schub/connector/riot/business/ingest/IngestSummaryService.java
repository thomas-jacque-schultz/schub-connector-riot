package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.AccountsByRank;
import schultz.thomas.schub.connector.riot.api.dto.IngestSummary;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.model.RankSpan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Comptages par index ou estimatedCount : jamais de parcours des parties.
@Service
@RequiredArgsConstructor
public class IngestSummaryService {

    private static final List<IngestTaskState> OUTSTANDING =
            List.of(IngestTaskState.PENDING, IngestTaskState.RUNNING, IngestTaskState.FAILED);
    private static final List<IngestTaskType> ENRICHMENT = List.of(
            IngestTaskType.MATCH_TIMELINE, IngestTaskType.MATCH_TIMELINE_DIGEST, IngestTaskType.MATCH_RANKS);
    private static final List<IngestTaskState> QUEUED = List.of(IngestTaskState.PENDING, IngestTaskState.RUNNING);
    private static final List<IngestTaskType> PROFILE_TASKS = List.of(
            IngestTaskType.PLAYER_IDS, IngestTaskType.PLAYER_PREVIEW, IngestTaskType.PLAYER_PREVIEW_SLOW);

    static final String UNRANKED = "UNRANKED";
    static final List<String> TIERS = List.of(UNRANKED, "IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD",
            "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER");

    private final MongoTemplate mongo;

    public IngestSummary summary() {
        return new IngestSummary(matches(), profiles());
    }

    private IngestSummary.Counts matches() {
        long retrieved = mongo.estimatedCount(CachedMatch.class);
        List<String> enriching = mongo.findDistinct(
                Query.query(Criteria.where("type").in(ENRICHMENT).and("state").in(OUTSTANDING)),
                "key", IngestTask.class, String.class);
        long enrichingStored = enriching.isEmpty() ? 0
                : mongo.count(Query.query(Criteria.where("_id").in(enriching)), CachedMatch.class);
        long pending = mongo.count(Query.query(
                Criteria.where("type").is(IngestTaskType.MATCH_DETAIL).and("state").in(QUEUED)), IngestTask.class);
        return new IngestSummary.Counts(retrieved, retrieved - enrichingStored, pending);
    }

    private IngestSummary.Counts profiles() {
        long retrieved = mongo.estimatedCount(PlayerHistoryCursor.class);
        List<String> busy = mongo.findDistinct(
                Query.query(Criteria.where("state").in(QUEUED).and("puuid").ne(null)),
                "puuid", IngestTask.class, String.class);
        long busyRetrieved = busy.isEmpty() ? 0
                : mongo.count(Query.query(Criteria.where("_id").in(busy)), PlayerHistoryCursor.class);
        long pending = mongo.count(Query.query(
                Criteria.where("type").in(PROFILE_TASKS).and("state").in(QUEUED)), IngestTask.class);
        return new IngestSummary.Counts(retrieved, retrieved - busyRetrieved, pending);
    }

    public AccountsByRank accountsByRank() {
        Map<String, Long> releves = parRang("riot_player_cursor", List.of(
                new Document("$lookup", new Document("from", RankSpan.COLLECTION)
                        .append("localField", "_id").append("foreignField", "puuid")
                        .append("pipeline", List.of(
                                new Document("$match", new Document("queue", QueueKind.RANKED_SOLO.name())),
                                new Document("$sort", new Document("lastSeenAt", -1)),
                                new Document("$limit", 1),
                                new Document("$project", new Document("_id", 0).append("tier", 1))))
                        .append("as", "rank")),
                new Document("$group", new Document("_id", new Document("$first", "$rank.tier"))
                        .append("n", new Document("$sum", 1)))));
        Map<String, Long> graines = parRang("riot_ladder_seed", List.of(
                new Document("$group", new Document("_id", "$tier").append("n", new Document("$sum", 1)))));

        List<AccountsByRank.Row> rows = new ArrayList<>();
        for (String tier : TIERS) {
            rows.add(new AccountsByRank.Row(tier, releves.getOrDefault(tier, 0L), graines.getOrDefault(tier, 0L)));
        }
        return new AccountsByRank(rows);
    }

    private Map<String, Long> parRang(String collection, List<Document> pipeline) {
        Map<String, Long> comptes = new HashMap<>();
        for (Document groupe : mongo.getCollection(collection).aggregate(pipeline).allowDiskUse(true)) {
            String tier = groupe.getString("_id");
            String cle = tier == null || !TIERS.contains(tier) ? UNRANKED : tier;
            comptes.merge(cle, ((Number) groupe.get("n")).longValue(), Long::sum);
        }
        return comptes;
    }
}
