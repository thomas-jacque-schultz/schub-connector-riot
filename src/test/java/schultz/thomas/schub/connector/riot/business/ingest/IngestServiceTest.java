package schultz.thomas.schub.connector.riot.business.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.api.dto.IngestStatus;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIngestStatus;
import schultz.thomas.schub.connector.riot.business.quota.RiotRateLimiter;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.IngestTaskRepository;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-21T10:00:00Z");

    @Mock private IngestQueue queue;
    @Mock private IngestTaskRepository tasks;
    @Mock private CachedMatchRepository matches;

    private RiotProperties properties;
    private RiotRateLimiter limiter;
    private IngestService service;

    @BeforeEach
    void setUp() {
        properties = new RiotProperties();
        TestClock clock = new TestClock(MAINTENANT);
        limiter = new RiotRateLimiter(properties.getQuota(), clock, duration -> {
            throw new UnsupportedOperationException("Le statut ne doit rien attendre.");
        });
        service = new IngestService(queue, tasks, matches, limiter, new IngestThroughput(clock), properties, clock);
    }

    @Test
    @DisplayName("une partie déjà stockée n'est pas empilée")
    void nEmpilePasUnePartieDejaStockee() {
        when(matches.findStoredIds(any())).thenReturn(List.of(
                new CachedMatch("EUW1_DEJA", null, MAINTENANT)));
        when(queue.enqueue(eq(IngestTaskType.MATCH_DETAIL), eq("EUW1_NEUVE"), anyString(), anyLong()))
                .thenReturn(true);

        int empilées = service.enqueueDetails("p1", List.of("EUW1_DEJA", "EUW1_NEUVE"), false);

        assertThat(empilées).isEqualTo(1);
        verify(queue, never()).enqueue(any(), eq("EUW1_DEJA"), anyString(), anyLong());
    }

    @Test
    @DisplayName("les parties de la collecte de fond passent après toutes celles des joueurs")
    void collecteDeFondEnDernier() {
        when(matches.findStoredIds(any())).thenReturn(List.of());

        service.enqueueDetails("p1", List.of("EUW1_7000000000"), true);

        verify(queue).enqueue(eq(IngestTaskType.MATCH_DETAIL), eq("EUW1_7000000000"), eq("p1"),
                eq(IngestTask.backgroundPriority(7_000_000_000L)));
        assertThat(IngestTask.backgroundPriority(Long.MAX_VALUE / 4)).isNegative();
    }

    @Test
    @DisplayName("le temps d'écoulement se calcule au débit autorisé, pas au nombre de tâches")
    void estimeLEcoulementAuDebitAutorise() {
        when(tasks.countByState(IngestTaskState.PENDING)).thenReturn(880L);
        when(tasks.countByState(IngestTaskState.RUNNING)).thenReturn(0L);
        when(tasks.countByState(IngestTaskState.FAILED)).thenReturn(0L);

        IngestStatus statut = service.status();

        assertThat(statut.callsPerMinute()).isEqualTo(44.0);
        assertThat(statut.estimatedDrain()).isEqualTo(Duration.ofMinutes(20));
        assertThat(statut.estimatedReadyAt()).isEqualTo(MAINTENANT.plus(Duration.ofMinutes(20)));
    }

    @Test
    @DisplayName("une pénalité 429 en cours s'ajoute à l'estimation")
    void ajouteLaPenaliteEnCours() {
        when(tasks.countByState(IngestTaskState.PENDING)).thenReturn(44L);
        when(tasks.countByState(IngestTaskState.RUNNING)).thenReturn(0L);
        when(tasks.countByState(IngestTaskState.FAILED)).thenReturn(0L);

        limiter.penalise(Duration.ofSeconds(30));

        IngestStatus statut = service.status();

        assertThat(statut.throttledFor()).isEqualTo(Duration.ofSeconds(30));
        assertThat(statut.estimatedDrain()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    @DisplayName("l'avancement d'un joueur compte ce qui passe devant lui, pas ses seules tâches")
    void estimeLaFinDUnJoueurDepuisToutCeQuiLePrecede() {
        when(tasks.countByPuuidAndState("p1", IngestTaskState.PENDING)).thenReturn(200L);
        when(tasks.countByPuuidAndState("p1", IngestTaskState.RUNNING)).thenReturn(0L);
        when(tasks.countByPuuidAndState("p1", IngestTaskState.FAILED)).thenReturn(0L);
        when(tasks.findFirstByPuuidAndStateOrderByPriorityAsc("p1", IngestTaskState.PENDING))
                .thenReturn(Optional.of(tache(7_990_000_000L)));
        when(tasks.countByStateAndPriorityGreaterThanEqual(IngestTaskState.PENDING, 7_990_000_000L))
                .thenReturn(880L);
        when(tasks.countByState(IngestTaskState.RUNNING)).thenReturn(0L);

        PlayerIngestStatus statut = service.statusOf("p1");

        assertThat(statut.queuedAhead()).isEqualTo(880L);
        assertThat(statut.estimatedRemaining()).isEqualTo(Duration.ofMinutes(20));
        assertThat(statut.estimatedReadyAt()).isEqualTo(MAINTENANT.plus(Duration.ofMinutes(20)));
    }

    @Test
    @DisplayName("un joueur sans rien en file n'a pas de date de fin — et pas l'instant courant")
    void nInventePasDeDateDeFinPourUnJoueurSansTravail() {
        when(tasks.countByPuuidAndState("inconnu", IngestTaskState.PENDING)).thenReturn(0L);
        when(tasks.countByPuuidAndState("inconnu", IngestTaskState.RUNNING)).thenReturn(0L);
        when(tasks.countByPuuidAndState("inconnu", IngestTaskState.FAILED)).thenReturn(0L);

        PlayerIngestStatus statut = service.statusOf("inconnu");

        assertThat(statut.pending()).isZero();
        assertThat(statut.estimatedReadyAt()).isNull();
        assertThat(statut.estimatedRemaining()).isNull();
    }

    private static IngestTask tache(long priorite) {
        return new IngestTask("MATCH_DETAIL:EUW1_1", IngestTaskType.MATCH_DETAIL, "EUW1_1", "p1",
                IngestTaskState.PENDING, priorite, MAINTENANT, MAINTENANT, null, 0, null);
    }
}
