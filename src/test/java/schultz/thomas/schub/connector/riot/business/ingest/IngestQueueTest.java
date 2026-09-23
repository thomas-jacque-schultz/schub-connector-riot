package schultz.thomas.schub.connector.riot.business.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestQueueTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-21T10:00:00Z");

    @Mock private MongoTemplate mongo;

    private IngestQueue queue;

    @BeforeEach
    void setUp() {
        queue = new IngestQueue(mongo, new TestClock(MAINTENANT));
    }

    @Test
    @DisplayName("empiler deux fois la même tâche n'en crée qu'une")
    void empilementIdempotent() {
        assertThat(queue.enqueue(IngestTaskType.MATCH_DETAIL, "EUW1_1", "p1", 1L)).isTrue();

        when(mongo.insert(any(IngestTask.class)))
                .thenThrow(new DuplicateKeyException("déjà en file"));

        assertThat(queue.enqueue(IngestTaskType.MATCH_DETAIL, "EUW1_1", "p1", 1L)).isFalse();
    }

    @Test
    @DisplayName("une tâche en cours dont le bail a expiré redevient réclamable")
    void rattrapeLesTachesOrphelines() {
        queue.claim(Duration.ofMinutes(15));

        String requête = captureQuery().getQueryObject().toString();
        assertThat(requête).contains("RUNNING").contains("leaseUntil").contains("$lt");
        assertThat(requête).contains("PENDING");
    }

    @Test
    @DisplayName("sans la collecte de fond, les priorités négatives ne sont pas réclamables")
    void excluLaCollecteDeFond() {
        queue.claim(Duration.ofMinutes(15), false);

        assertThat(captureQuery().getQueryObject().toString()).contains("priority").contains("$gte");
    }

    @Test
    @DisplayName("la réclamation sert les parties récentes d'abord")
    void servLesPartiesRecentesDAbord() {
        queue.claim(Duration.ofMinutes(15));

        assertThat(captureQuery().getSortObject().toString()).contains("priority=-1");
    }

    @Test
    @DisplayName("un 429 rend la tâche à la file sans compter d'échec")
    void unQuotaSatureNeConsommePasLaTache() {
        IngestTask task = task(2);

        queue.release(task, Duration.ofSeconds(30));

        String mise = captureUpdate().getUpdateObject().toString();
        assertThat(mise).contains("PENDING");
        assertThat(mise).doesNotContain("attempts");
    }

    @Test
    @DisplayName("une tâche n'est abandonnée qu'au bout de ses tentatives")
    void abandonneSeulementAuPlafond() {
        queue.fail(task(1), "Riot muet", 3, Duration.ofMinutes(1));
        assertThat(captureUpdate().getUpdateObject().toString()).contains("PENDING");
    }

    @Test
    @DisplayName("la dernière tentative épuisée passe la tâche en échec, pas en attente")
    void passeEnEchecAuPlafond() {
        queue.fail(task(2), "Riot muet", 3, Duration.ofMinutes(1));
        assertThat(captureUpdate().getUpdateObject().toString()).contains("FAILED");
    }

    private IngestTask task(int attempts) {
        return new IngestTask("MATCH_DETAIL:EUW1_1", IngestTaskType.MATCH_DETAIL, "EUW1_1", "p1",
                IngestTaskState.RUNNING, 1L, MAINTENANT, MAINTENANT, MAINTENANT, attempts, null);
    }

    private Query captureQuery() {
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(query.capture(), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(IngestTask.class));
        return query.getValue();
    }

    private UpdateDefinition captureUpdate() {
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(IngestTask.class));
        return update.getValue();
    }
}
