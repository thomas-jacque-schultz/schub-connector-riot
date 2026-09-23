package schultz.thomas.schub.connector.riot.business.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.api.dto.HistorySyncReport;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailsResponse;
import schultz.thomas.schub.connector.riot.api.dto.MatchHistory;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.api.dto.IngestEnqueueReport;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.model.PlayerMatchRef;
import schultz.thomas.schub.connector.riot.data.repository.PlayerHistoryCursorRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerMatchRefRepository;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchHistoryServiceTest {

    private static final String PUUID = "puuid-de-test";
    private static final Instant MAINTENANT = Instant.parse("2026-09-18T12:00:00Z");

    @Mock private RiotApiClient riotApiClient;
    @Mock private PlayerMatchRefRepository playerMatches;
    @Mock private PlayerHistoryCursorRepository cursors;
    @Mock private MatchDetailService matchDetailService;
    @Mock private IngestService ingestService;

    private RiotProperties properties;
    private TestClock clock;
    private MatchHistoryService service;

    @BeforeEach
    void setUp() {
        properties = new RiotProperties();
        clock = new TestClock(MAINTENANT);
        service = new MatchHistoryService(riotApiClient, playerMatches, cursors,
                matchDetailService, ingestService, properties, clock);
    }

    @Test
    @DisplayName("sans curseur, le premier remplissage remonte la profondeur configurée")
    void premierRemplissageRemonteLaProfondeur() {
        properties.getCache().setHistoryDepth(Duration.ofDays(365));
        when(cursors.findById(PUUID)).thenReturn(Optional.empty());
        when(riotApiClient.matchIds(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID)).thenReturn(List.of());
        when(playerMatches.findFirstByPuuidAndPlayedAtNotNullOrderByPlayedAtDesc(PUUID)).thenReturn(Optional.empty());

        HistorySyncReport rapport = service.sync(PUUID);

        assertThat(rapport.queriedFrom()).isEqualTo(MAINTENANT.minus(Duration.ofDays(365)));
    }

    @Test
    @DisplayName("avec un curseur, on repart du dernier relevé MOINS une heure de recouvrement")
    void repartDuDernierReleveMoinsUneHeure() {
        Instant dernierReleve = MAINTENANT.minus(Duration.ofHours(6));
        when(cursors.findById(PUUID)).thenReturn(Optional.of(
                new PlayerHistoryCursor(PUUID, MAINTENANT.minus(Duration.ofDays(30)),
                        dernierReleve, null)));
        when(riotApiClient.matchIds(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID)).thenReturn(List.of());
        when(playerMatches.findFirstByPuuidAndPlayedAtNotNullOrderByPlayedAtDesc(PUUID)).thenReturn(Optional.empty());

        service.sync(PUUID);

        ArgumentCaptor<Instant> borne = ArgumentCaptor.forClass(Instant.class);
        verify(riotApiClient).matchIds(eq(PUUID), borne.capture(), anyInt(), anyInt());

        assertThat(borne.getValue()).isEqualTo(dernierReleve.minus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("les ids déjà connus ne sont pas réécrits : le recouvrement en ramène forcément")
    void dedoublonneSurLeMatchId() {
        when(cursors.findById(PUUID)).thenReturn(Optional.empty());
        when(riotApiClient.matchIds(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of("EUW1_DEJA_VU", "EUW1_NOUVEAU"));
        when(playerMatches.findAllById(any())).thenReturn(List.of(
                new PlayerMatchRef(PlayerMatchRef.idOf(PUUID, "EUW1_DEJA_VU"), PUUID,
                        "EUW1_DEJA_VU", MAINTENANT.minus(Duration.ofHours(2)), MAINTENANT)));
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID)).thenReturn(List.of());
        when(playerMatches.findFirstByPuuidAndPlayedAtNotNullOrderByPlayedAtDesc(PUUID)).thenReturn(Optional.empty());

        HistorySyncReport rapport = service.sync(PUUID);

        assertThat(rapport.idsSeen()).isEqualTo(2);
        assertThat(rapport.idsNew()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlayerMatchRef>> ecrits = ArgumentCaptor.forClass(List.class);
        verify(playerMatches).saveAll(ecrits.capture());
        assertThat(ecrits.getValue()).extracting(PlayerMatchRef::matchId)
                .containsExactly("EUW1_NOUVEAU");
    }

    @Test
    @DisplayName("le curseur retient le début de la synchronisation, pas sa fin")
    void leCurseurRetientLeDebut() {
        when(cursors.findById(PUUID)).thenReturn(Optional.empty());
        when(riotApiClient.matchIds(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            clock.advance(Duration.ofMinutes(3));
            return List.of();
        });
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID)).thenReturn(List.of());
        when(playerMatches.findFirstByPuuidAndPlayedAtNotNullOrderByPlayedAtDesc(PUUID)).thenReturn(Optional.empty());

        service.sync(PUUID);

        ArgumentCaptor<PlayerHistoryCursor> curseur =
                ArgumentCaptor.forClass(PlayerHistoryCursor.class);
        verify(cursors).save(curseur.capture());
        assertThat(curseur.getValue().lastSyncStartedAt()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("une lecture ne rappelle pas Riot tant que le relevé est frais")
    void neRappellePasRiotQuandCEstFrais() {
        properties.getCache().setHistoryFreshness(Duration.ofMinutes(15));
        when(cursors.findById(PUUID)).thenReturn(Optional.of(new PlayerHistoryCursor(
                PUUID, MAINTENANT.minus(Duration.ofDays(1)),
                MAINTENANT.minus(Duration.ofMinutes(5)), null)));
        when(playerMatches.findByPuuidAndPlayedAtGreaterThanEqualOrderByPlayedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID)).thenReturn(List.of());

        MatchHistory historique = service.history(PUUID, Instant.EPOCH);

        assertThat(historique.ingestQueued()).isFalse();
        verify(ingestService, never()).enqueuePlayer(anyString());
        verify(riotApiClient, never()).matchIds(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("une lecture empile la collecte, elle ne la fait pas")
    void uneLectureEmpileMaisNAppellePasRiot() {
        when(cursors.findById(PUUID)).thenReturn(Optional.empty());
        when(ingestService.enqueuePlayer(PUUID))
                .thenReturn(new IngestEnqueueReport(PUUID, true, null));
        when(playerMatches.findByPuuidAndPlayedAtGreaterThanEqualOrderByPlayedAtDesc(any(), any()))
                .thenReturn(List.of(new PlayerMatchRef(PlayerMatchRef.idOf(PUUID, "EUW1_1"),
                        PUUID, "EUW1_1", MAINTENANT.minus(Duration.ofDays(1)), MAINTENANT)));
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID)).thenReturn(List.of());

        MatchHistory historique = service.history(PUUID, Instant.EPOCH);

        assertThat(historique.matchIds()).containsExactly("EUW1_1");
        assertThat(historique.ingestQueued()).isTrue();
        verify(riotApiClient, never()).matchIds(anyString(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("une partie sans date connue est rendue plutôt que tue")
    void rendLesPartiesSansDate() {
        when(cursors.findById(PUUID)).thenReturn(Optional.of(new PlayerHistoryCursor(
                PUUID, MAINTENANT, MAINTENANT, null)));
        when(playerMatches.findByPuuidAndPlayedAtGreaterThanEqualOrderByPlayedAtDesc(any(), any()))
                .thenReturn(List.of(new PlayerMatchRef(PlayerMatchRef.idOf(PUUID, "EUW1_DATEE"),
                        PUUID, "EUW1_DATEE", MAINTENANT.minus(Duration.ofDays(1)), MAINTENANT)));
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID))
                .thenReturn(List.of(new PlayerMatchRef(PlayerMatchRef.idOf(PUUID, "EUW1_SANS_DATE"),
                        PUUID, "EUW1_SANS_DATE", null, MAINTENANT)));

        MatchHistory historique = service.history(PUUID, Instant.EPOCH);

        assertThat(historique.matchIds()).containsExactly("EUW1_SANS_DATE", "EUW1_DATEE");
    }

    @Test
    @DisplayName("les détails manquants sont récupérés dans la foulée, par paquets bornés")
    void recupereLesDetailsManquants() {
        when(cursors.findById(PUUID)).thenReturn(Optional.empty());
        when(riotApiClient.matchIds(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(playerMatches.findByPuuidAndPlayedAtIsNull(PUUID)).thenReturn(List.of(
                new PlayerMatchRef(PlayerMatchRef.idOf(PUUID, "EUW1_1"), PUUID, "EUW1_1", null,
                        MAINTENANT)));
        when(matchDetailService.details(List.of("EUW1_1")))
                .thenReturn(new MatchDetailsResponse(List.of(), List.of("EUW1_1"), List.of()));
        when(playerMatches.findFirstByPuuidAndPlayedAtNotNullOrderByPlayedAtDesc(PUUID)).thenReturn(Optional.empty());

        HistorySyncReport rapport = service.sync(PUUID);

        assertThat(rapport.detailsPending()).isEqualTo(1);
    }
}
