package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.BulkOperations;
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
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class IngestQueue {

    private final MongoTemplate mongo;
    private final Clock clock;

    // Une seule réplique, arrêtée avant sa relève : au démarrage, une tâche en cours est celle d'un processus mort.
    // Elle repart tout de suite plutôt qu'au bout de son bail, qui bloquerait une maintenance un quart d'heure.
    @EventListener(ApplicationReadyEvent.class)
    public void requeueOrphans() {
        long orphelines = mongo.updateMulti(Query.query(Criteria.where("state").is(IngestTaskState.RUNNING)),
                new Update().set("state", IngestTaskState.PENDING).unset("leaseUntil"), IngestTask.class)
                .getModifiedCount();
        if (orphelines > 0) {
            log.info("{} tâche(s) laissée(s) en cours par le processus précédent : remises en file.", orphelines);
        }
    }

    // Avant riot#37, la priorité de fond ne portait que la séquence : ces tâches tombent toutes dans la tranche de la
    // minute la plus lointaine. Reclassées par leur date de mise en file, qui est celle du relevé du compte.
    public int reprioritizeLegacyBackground() {
        Query anciennes = Query.query(Criteria.where("priority").gte(IngestTask.backgroundPriority(Instant.MAX, 0))
                .lte(IngestTask.backgroundPriority(Instant.MAX, Long.MAX_VALUE)));
        anciennes.fields().include("key", "enqueuedAt");
        String collection = mongo.getCollectionName(IngestTask.class);
        List<Document> taches = mongo.find(anciennes, Document.class, collection);
        if (taches.isEmpty()) {
            return 0;
        }
        BulkOperations lot = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, collection);
        for (Document tache : taches) {
            long priorite = IngestTask.backgroundPriority(tache.getDate("enqueuedAt").toInstant(),
                    IngestTask.sequenceOf(tache.getString("key")));
            lot.updateOne(Query.query(Criteria.where("_id").is(tache.get("_id"))),
                    new Update().set("priority", priorite));
        }
        lot.execute();
        return taches.size();
    }

    public boolean enqueue(IngestTaskType type, String key, String puuid, long priority) {
        return enqueue(type, key, puuid, priority, clock.instant());
    }

    public boolean enqueue(IngestTaskType type, String key, String puuid, long priority, Instant notBefore) {
        Instant now = clock.instant();
        IngestTask task = new IngestTask(IngestTask.idOf(type, key), type, key, puuid,
                IngestTaskState.PENDING, priority, now, notBefore, null, 0, null);
        try {
            mongo.insert(task);
            return true;
        } catch (DuplicateKeyException alreadyQueued) {
            return raise(task);
        }
    }

    // Déjà en file plus bas : un joueur actif remonte la tâche qu'un aperçu ou la collecte de fond avait posée.
    private boolean raise(IngestTask task) {
        Update update = new Update().set("priority", task.priority()).min("notBefore", task.notBefore());
        if (task.puuid() != null) {
            update.set("puuid", task.puuid());
        }
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(task.id())
                        .and("state").is(IngestTaskState.PENDING).and("priority").lt(task.priority())),
                update, IngestTask.class).getModifiedCount() > 0;
    }

    public Optional<IngestTask> claim(Duration lease) {
        return claim(lease, true);
    }

    // Sans la collecte de fond : ses tâches restent en file, intactes, jusqu'à sa réactivation.
    // Index {state, priority} : la file se lit dans l'ordre et la lecture s'arrête à la première tâche mûre.
    public Optional<IngestTask> claim(Duration lease, boolean includeBackground) {
        return claim(lease, includeBackground ? Long.MIN_VALUE : 0);
    }

    public Optional<IngestTask> claim(Duration lease, long floor) {
        Instant now = clock.instant();
        requeueExpired(now);

        Criteria mures = Criteria.where("state").is(IngestTaskState.PENDING).and("notBefore").lte(now);
        if (floor > Long.MIN_VALUE) {
            mures = mures.and("priority").gte(floor);
        }
        Query query = Query.query(mures).with(Sort.by(Sort.Direction.DESC, "priority"));

        Update update = new Update()
                .set("state", IngestTaskState.RUNNING)
                .set("leaseUntil", now.plus(lease));

        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), IngestTask.class));
    }

    // Bail expiré : l'ouvrier est mort en route, la tâche repart avec sa priorité.
    private void requeueExpired(Instant now) {
        mongo.updateMulti(Query.query(Criteria.where("state").is(IngestTaskState.RUNNING).and("leaseUntil").lt(now)),
                new Update().set("state", IngestTaskState.PENDING).unset("leaseUntil"),
                IngestTask.class);
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
