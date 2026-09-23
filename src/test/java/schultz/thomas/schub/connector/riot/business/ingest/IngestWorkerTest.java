package schultz.thomas.schub.connector.riot.business.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLane;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLaneContext;
import schultz.thomas.schub.connector.riot.business.services.IdSyncResult;
import schultz.thomas.schub.connector.riot.business.services.MatchDetailService;
import schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestWorkerTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-21T10:00:00Z");

    @Mock private IngestQueue queue;
    @Mock private IngestService ingestService;
    @Mock private MatchHistoryService historyService;
    @Mock private MatchDetailService matchDetailService;
    @Mock private MatchEnrichmentService enrichment;

    private IngestWorker worker;

    @BeforeEach
    void setUp() {
        worker = new IngestWorker(queue, ingestService, historyService, matchDetailService,
                enrichment, new RiotProperties());
    }

    @Test
    @DisplayName("un 429 en cours de file rend la tâche et arrête le tour")
    void un429RendLaTacheEtArreteLeTour() {
        when(queue.claim(any())).thenReturn(Optional.of(detailTask()));
        when(matchDetailService.detail("EUW1_1"))
                .thenThrow(new RiotQuotaExceededException("saturé", Duration.ofSeconds(2)));

        worker.drain();

        verify(queue).release(any(IngestTask.class), any(Duration.class));
        verify(queue, never()).fail(any(), anyString(), anyInt(), any());
        verify(queue, never()).complete(any());
        verify(queue, times(1)).claim(any());
    }

    @Test
    @DisplayName("une panne de Riot compte un échec, mais le tour continue")
    void unePanneCompteUnEchecEtLeTourContinue() {
        when(queue.claim(any()))
                .thenReturn(Optional.of(detailTask()))
                .thenReturn(Optional.empty());
        when(matchDetailService.detail("EUW1_1")).thenThrow(new RiotApiException("Riot muet"));

        worker.drain();

        verify(queue).fail(any(IngestTask.class), eq("Riot muet"), anyInt(), any(Duration.class));
        verify(queue, times(2)).claim(any());
    }

    @Test
    @DisplayName("un relevé d'identifiants empile un détail par partie manquante")
    void unReleveEmpileLesDetails() {
        IngestTask task = new IngestTask("PLAYER_IDS:p1", IngestTaskType.PLAYER_IDS, "p1", "p1",
                IngestTaskState.RUNNING, Long.MAX_VALUE, MAINTENANT, MAINTENANT, null, 0, null);
        when(queue.claim(any())).thenReturn(Optional.of(task)).thenReturn(Optional.empty());
        when(historyService.syncIds("p1")).thenReturn(
                new IdSyncResult("p1", MAINTENANT, List.of("EUW1_1", "EUW1_2"), 2, MAINTENANT));

        worker.drain();

        verify(ingestService).enqueueDetails("p1", List.of("EUW1_1", "EUW1_2"));
        verify(queue).complete(task);
    }

    @Test
    @DisplayName("une partie purgée par Riot est consommée, pas retentée sans fin")
    void unePartiePurgeeEstConsommee() {
        IngestTask task = detailTask();
        when(queue.claim(any())).thenReturn(Optional.of(task)).thenReturn(Optional.empty());
        when(matchDetailService.detail("EUW1_1")).thenReturn(Optional.empty());

        worker.drain();

        verify(queue).complete(task);
        verify(queue, never()).fail(any(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("l'ouvrier travaille sur la voie de collecte, et la rend en sortant")
    void travailleSurLaVoieDeCollecte() {
        AtomicReference<QuotaLane> vue = new AtomicReference<>();
        when(queue.claim(any())).thenReturn(Optional.of(detailTask())).thenReturn(Optional.empty());
        when(matchDetailService.detail("EUW1_1")).thenAnswer(appel -> {
            vue.set(QuotaLaneContext.current());
            return Optional.empty();
        });

        worker.drain();

        assertThat(vue.get()).isEqualTo(QuotaLane.BULK);
        assertThat(QuotaLaneContext.current()).isEqualTo(QuotaLane.INTERACTIVE);
    }

    private IngestTask detailTask() {
        return new IngestTask("MATCH_DETAIL:EUW1_1", IngestTaskType.MATCH_DETAIL, "EUW1_1", "p1",
                IngestTaskState.RUNNING, 1L, MAINTENANT, MAINTENANT, MAINTENANT, 0, null);
    }
}
