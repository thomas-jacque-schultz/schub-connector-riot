package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.IngestSummary;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;

import java.util.List;

// Comptages par index ou estimatedCount : jamais de parcours des parties.
@Service
@RequiredArgsConstructor
public class IngestSummaryService {

    private static final List<IngestTaskState> OUTSTANDING =
            List.of(IngestTaskState.PENDING, IngestTaskState.RUNNING, IngestTaskState.FAILED);
    private static final List<IngestTaskType> ENRICHMENT = List.of(
            IngestTaskType.MATCH_TIMELINE, IngestTaskType.MATCH_TIMELINE_DIGEST, IngestTaskType.MATCH_RANKS);
    private static final List<IngestTaskState> QUEUED = List.of(IngestTaskState.PENDING, IngestTaskState.RUNNING);

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
                Criteria.where("type").is(IngestTaskType.PLAYER_IDS).and("state").in(QUEUED)), IngestTask.class);
        return new IngestSummary.Counts(retrieved, retrieved - busyRetrieved, pending);
    }
}
