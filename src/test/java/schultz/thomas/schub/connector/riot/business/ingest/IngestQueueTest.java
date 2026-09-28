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
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(IngestTask.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0, 0L, null));

        assertThat(queue.enqueue(IngestTaskType.MATCH_DETAIL, "EUW1_1", "p1", 1L)).isFalse();
    }

    @Test
    @DisplayName("une tâche déjà en file plus bas est remontée à la priorité demandée, sans toucher à une tâche en cours")
    void remonteUneTacheEnAttente() {
        when(mongo.insert(any(IngestTask.class))).thenThrow(new DuplicateKeyException("déjà en file"));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(IngestTask.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null));

        assertThat(queue.enqueue(IngestTaskType.PLAYER_IDS, "p1", "p1", Long.MAX_VALUE)).isTrue();

        ArgumentCaptor<Query> cible = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> remontee = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).updateFirst(cible.capture(), remontee.capture(), eq(IngestTask.class));
        assertThat(cible.getValue().getQueryObject().toString()).contains("PENDING").contains("$lt");
        assertThat(remontee.getValue().getUpdateObject().toString()).contains("priority").contains("$min");
    }

    @Test
    @DisplayName("une tâche en cours dont le bail a expiré redevient réclamable")
    void rattrapeLesTachesOrphelines() {
        queue.claim(Duration.ofMinutes(15));

        ArgumentCaptor<Query> orphelines = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> remise = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).updateMulti(orphelines.capture(), remise.capture(), eq(IngestTask.class));
        assertThat(orphelines.getValue().getQueryObject().toString())
                .contains("RUNNING").contains("leaseUntil").contains("$lt");
        assertThat(remise.getValue().getUpdateObject().toString()).contains("PENDING");
        assertThat(captureQuery().getQueryObject().toString()).contains("PENDING").doesNotContain("$or");
    }

    @Test
    @DisplayName("au démarrage, toute tâche en cours est remise en file, bail expiré ou non")
    void remetEnFileAuDemarrage() {
        when(mongo.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(IngestTask.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(16, 16L, null));

        queue.requeueOrphans();

        ArgumentCaptor<Query> enCours = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> remise = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).updateMulti(enCours.capture(), remise.capture(), eq(IngestTask.class));
        assertThat(enCours.getValue().getQueryObject().toString()).contains("RUNNING").doesNotContain("leaseUntil");
        assertThat(remise.getValue().getUpdateObject().toString()).contains("PENDING");
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
