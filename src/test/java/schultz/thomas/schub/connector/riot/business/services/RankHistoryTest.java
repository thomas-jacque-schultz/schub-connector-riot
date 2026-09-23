package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.data.model.RankSpan;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankHistoryTest {

    private static final Instant HIER = Instant.parse("2026-09-22T10:00:00Z");
    private static final Instant AUJOURDHUI = Instant.parse("2026-09-23T10:00:00Z");

    @Mock private MongoTemplate mongo;
    @Mock private BulkOperations bulk;

    private RankHistory history;

    @BeforeEach
    void setUp() {
        history = new RankHistory(mongo);
    }

    @Test
    @DisplayName("le même rang relevé une seconde fois prolonge la plage, sans en ouvrir une autre")
    void prolongeLaPlage() {
        when(mongo.find(any(Query.class), eq(RankSpan.class))).thenReturn(List.of(plage("GOLD", "II", HIER)));
        when(mongo.bulkOps(BulkOperations.BulkMode.ORDERED, RankSpan.class)).thenReturn(bulk);

        history.record("p1", List.of(solo("GOLD", "II", AUJOURDHUI)));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(bulk).updateOne(any(Query.class), update.capture());
        assertThat(update.getValue().getUpdateObject().get("$max", Document.class))
                .containsEntry("lastSeenAt", AUJOURDHUI);
        verify(bulk, never()).insert(any(Object.class));
    }

    @Test
    @DisplayName("un changement de division ouvre une nouvelle plage : l'ancienne dit jusqu'à quand il était Or II")
    void ouvreUnePlageAuChangement() {
        when(mongo.find(any(Query.class), eq(RankSpan.class))).thenReturn(List.of(plage("GOLD", "II", HIER)));
        when(mongo.bulkOps(BulkOperations.BulkMode.ORDERED, RankSpan.class)).thenReturn(bulk);

        history.record("p1", List.of(solo("GOLD", "I", AUJOURDHUI)));

        ArgumentCaptor<RankSpan> ouverte = ArgumentCaptor.forClass(RankSpan.class);
        verify(bulk).insert(ouverte.capture());
        assertThat(ouverte.getValue().division()).isEqualTo("I");
        assertThat(ouverte.getValue().firstSeenAt()).isEqualTo(AUJOURDHUI);
        verify(bulk, never()).updateOne(any(Query.class), any(Update.class));
    }

    @Test
    @DisplayName("deux relevés d'un même lot s'enchaînent : le second prolonge la plage que le premier a ouverte")
    void enchaineDansUnLot() {
        when(mongo.find(any(Query.class), eq(RankSpan.class))).thenReturn(List.of());
        when(mongo.bulkOps(BulkOperations.BulkMode.ORDERED, RankSpan.class)).thenReturn(bulk);

        history.recordAll(List.of(
                new RankHistory.Observation("p1", solo("GOLD", "I", AUJOURDHUI)),
                new RankHistory.Observation("p1", solo("GOLD", "I", HIER))));

        verify(bulk, times(1)).insert(any(RankSpan.class));
        verify(bulk, times(1)).updateOne(any(Query.class), any(Update.class));
    }

    @Test
    @DisplayName("un classement sans palier ou hors solo et flex n'écrit rien")
    void ignoreLesRelevesInutilisables() {
        RankedStanding arene = new RankedStanding(QueueKind.OTHER, "CHERRY", "GOLD", "I", 0, 0, 0,
                false, false, AUJOURDHUI);

        history.record("p1", List.of(arene, solo(null, null, AUJOURDHUI)));

        verifyNoInteractions(mongo);
    }

    @Test
    @DisplayName("le palier retenu est celui du solo, le flex seulement à défaut")
    void prefereLeSolo() {
        RankSpan flex = new RankSpan("f", "p1", QueueKind.RANKED_FLEX, "DIAMOND", "IV", 0, HIER, AUJOURDHUI);
        RankSpan flexSeul = new RankSpan("g", "p2", QueueKind.RANKED_FLEX, "SILVER", "I", 0, HIER, AUJOURDHUI);
        when(mongo.find(any(Query.class), eq(RankSpan.class)))
                .thenReturn(List.of(flex, plage("GOLD", "II", HIER), flexSeul));

        assertThat(history.latestTiers(List.of("p1", "p2", "p3")))
                .containsEntry("p1", "GOLD")
                .containsEntry("p2", "SILVER")
                .doesNotContainKey("p3");
    }

    @Test
    @DisplayName("le rang d'une partie : le solo avant le flex, et la plage la plus proche de la date")
    void rangAuMomentDeLaPartie() {
        RankSpan flex = new RankSpan("f", "p1", QueueKind.RANKED_FLEX, "DIAMOND", "IV", 0, HIER, AUJOURDHUI);
        RankSpan ancien = new RankSpan("a", "p1", QueueKind.RANKED_SOLO, "SILVER", "I", 0,
                HIER.minus(Duration.ofDays(40)), HIER.minus(Duration.ofDays(20)));
        RankSpan courant = new RankSpan("c", "p1", QueueKind.RANKED_SOLO, "GOLD", "IV", 0, HIER, AUJOURDHUI);
        when(mongo.find(any(Query.class), eq(RankSpan.class))).thenReturn(List.of(flex, ancien, courant));

        assertThat(history.rankAt(List.of("p1"), HIER.plus(Duration.ofHours(2)), Duration.ofDays(30)))
                .containsEntry("p1", courant);
    }

    private static RankSpan plage(String tier, String division, Instant vu) {
        return new RankSpan("s1", "p1", QueueKind.RANKED_SOLO, tier, division, 50, vu, vu);
    }

    private static RankedStanding solo(String tier, String division, Instant vu) {
        return new RankedStanding(QueueKind.RANKED_SOLO, "RANKED_SOLO_5x5", tier, division, 50, 10, 10,
                false, false, vu);
    }
}
