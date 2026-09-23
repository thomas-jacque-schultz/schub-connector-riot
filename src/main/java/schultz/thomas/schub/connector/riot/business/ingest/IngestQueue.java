package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class IngestQueue {

    private final MongoTemplate mongo;
    private final Clock clock;

    public boolean enqueue(IngestTaskType type, String key, String puuid, long priority) {
        Instant now = clock.instant();
        IngestTask task = new IngestTask(IngestTask.idOf(type, key), type, key, puuid,
                IngestTaskState.PENDING, priority, now, now, null, 0, null);
        try {
            mongo.insert(task);
            return true;
        } catch (DuplicateKeyException alreadyQueued) {
            return false;
        }
    }

    public Optional<IngestTask> claim(Duration lease) {
        return claim(lease, true);
    }

    // Sans la collecte de fond : ses tâches restent en file, intactes, jusqu'à sa réactivation.
    public Optional<IngestTask> claim(Duration lease, boolean includeBackground) {
        Instant now = clock.instant();
        Criteria claimable = new Criteria().orOperator(
                Criteria.where("state").is(IngestTaskState.PENDING),
                new Criteria().andOperator(
                        Criteria.where("state").is(IngestTaskState.RUNNING),
                        Criteria.where("leaseUntil").lt(now)));

        Criteria eligible = includeBackground
                ? Criteria.where("notBefore").lte(now)
                : Criteria.where("notBefore").lte(now).and("priority").gte(0);
        Query query = Query.query(new Criteria().andOperator(eligible, claimable))
                .with(Sort.by(Sort.Direction.DESC, "priority"));

        Update update = new Update()
                .set("state", IngestTaskState.RUNNING)
                .set("leaseUntil", now.plus(lease));

        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), IngestTask.class));
    }

    public void complete(IngestTask task) {
        mongo.remove(Query.query(Criteria.where("_id").is(task.id())), IngestTask.class);
    }

    public void release(IngestTask task, Duration delay) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(task.id())),
                new Update()
                        .set("state", IngestTaskState.PENDING)
                        .set("notBefore", clock.instant().plus(delay))
                        .unset("leaseUntil"),
                IngestTask.class);
    }

    public void fail(IngestTask task, String reason, int maxAttempts, Duration backoff) {
        int attempts = task.attempts() + 1;
        boolean exhausted = attempts >= maxAttempts;

        Update update = new Update()
                .set("attempts", attempts)
                .set("lastError", reason)
                .set("state", exhausted ? IngestTaskState.FAILED : IngestTaskState.PENDING)
                .set("notBefore", clock.instant().plus(backoff))
                .unset("leaseUntil");

        mongo.updateFirst(Query.query(Criteria.where("_id").is(task.id())), update, IngestTask.class);

        if (exhausted) {
            log.warn("Tâche d'ingestion abandonnée après {} tentatives : {} — {}",
                    attempts, task.id(), reason);
        }
    }

    public long retryFailed() {
        return mongo.updateMulti(
                Query.query(Criteria.where("state").is(IngestTaskState.FAILED)),
                new Update()
                        .set("state", IngestTaskState.PENDING)
                        .set("attempts", 0)
                        .set("notBefore", clock.instant())
                        .unset("lastError"),
                IngestTask.class).getModifiedCount();
    }
}
