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

/**
 * La mécanique de la file : empiler sans doublon, réclamer sans collision, rendre sans perdre.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class IngestQueue {

    private final MongoTemplate mongo;
    private final Clock clock;

    /**
     * Empile si la tâche n'existe pas déjà.
     *
     * <p>L'insertion échoue sur doublon de {@code _id} et c'est la réponse voulue : une tâche
     * déjà en file, déjà en cours, ou déjà en échec, ne doit pas être réarmée par un
     * empilement. Réarmer les échecs est une décision explicite, pas un effet de bord.</p>
     *
     * @return {@code true} si la tâche a bien été créée.
     */
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

    /**
     * Réclame la tâche la plus prioritaire, atomiquement.
     *
     * <p>La condition retient aussi les tâches {@code RUNNING} dont le bail a expiré. Sans
     * elle, une tâche réclamée par un processus tué resterait {@code RUNNING} pour toujours :
     * perdue, et invisible puisque les compteurs la montreraient « en cours ». C'est la reprise
     * après redémarrage, et elle ne coûte pas de balayage séparé.</p>
     */
    public Optional<IngestTask> claim(Duration lease) {
        Instant now = clock.instant();
        Criteria claimable = new Criteria().orOperator(
                Criteria.where("state").is(IngestTaskState.PENDING),
                new Criteria().andOperator(
                        Criteria.where("state").is(IngestTaskState.RUNNING),
                        Criteria.where("leaseUntil").lt(now)));

        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("notBefore").lte(now), claimable))
                .with(Sort.by(Sort.Direction.DESC, "priority"));

        Update update = new Update()
                .set("state", IngestTaskState.RUNNING)
                .set("leaseUntil", now.plus(lease));

        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), IngestTask.class));
    }

    /** La tâche est faite : plus rien à en garder, {@code riot_match} est la trace. */
    public void complete(IngestTask task) {
        mongo.remove(Query.query(Criteria.where("_id").is(task.id())), IngestTask.class);
    }

    /**
     * Rend la tâche sans compter d'échec — cas du 429.
     *
     * <p>Un quota saturé n'est pas une erreur de la tâche : la consommer l'écarterait
     * définitivement alors qu'elle n'a rien de fautif.</p>
     */
    public void release(IngestTask task, Duration delay) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(task.id())),
                new Update()
                        .set("state", IngestTaskState.PENDING)
                        .set("notBefore", clock.instant().plus(delay))
                        .unset("leaseUntil"),
                IngestTask.class);
    }

    /** Compte une tentative ratée, et abandonne au-delà du plafond. */
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

    /** Réarme les tâches abandonnées. Décision explicite : voir {@link #enqueue}. */
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
