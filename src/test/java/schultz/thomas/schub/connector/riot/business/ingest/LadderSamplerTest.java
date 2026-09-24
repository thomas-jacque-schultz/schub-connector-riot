package schultz.thomas.schub.connector.riot.business.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.LadderBound;
import schultz.thomas.schub.connector.riot.data.model.LadderSeed;
import schultz.thomas.schub.connector.riot.data.model.MatchLobby;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueEntryResponse;
import schultz.thomas.schub.connector.riot.data.repository.IngestTaskRepository;
import schultz.thomas.schub.connector.riot.data.repository.LadderBoundRepository;
import schultz.thomas.schub.connector.riot.data.repository.LadderSeedRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchLobbyRepository;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LadderSamplerTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-23T10:00:00Z");

    @Mock private RiotApiClient riotApiClient;
    @Mock private RankHistory rankHistory;
    @Mock private IngestQueue queue;
    @Mock private IngestTaskRepository tasks;
    @Mock private LadderSeedRepository seeds;
    @Mock private LadderBoundRepository bounds;
    @Mock private MatchLobbyRepository lobbies;
    @Mock private BackgroundCrawler crawler;

    private RiotProperties properties;
    private LadderSampler sampler;

    @BeforeEach
    void setUp() {
        properties = new RiotProperties();
        sampler = new LadderSampler(properties, riotApiClient, new RiotStatsMapper(), rankHistory, queue, tasks,
                seeds, bounds, lobbies, crawler, new TestClock(MAINTENANT));
        sampler.random = new Random(42);
    }

    @Test
    @DisplayName("un palier sous sa cible reçoit plusieurs pages à lire, un palier plein n'en reçoit pas")
    void empileUnePageParPalierEnManque() {
        when(crawler.active()).thenReturn(true);
        when(seeds.countByGroupAndSampledAtGreaterThanEqual(anyString(), any())).thenReturn(300L);
        when(seeds.countByGroupAndSampledAtGreaterThanEqual(eq("GOLD"), any())).thenReturn(12L);

        sampler.round();

        ArgumentCaptor<String> cle = ArgumentCaptor.forClass(String.class);
        verify(queue, times(properties.getSampling().getPagesInFlight())).enqueue(eq(IngestTaskType.LADDER_PAGE),
                cle.capture(), eq(null), eq(IngestTask.BACKGROUND_PLAYER_PRIORITY));
        assertThat(cle.getAllValues()).allMatch(page -> page.matches("GOLD/(I|II|III|IV)/\\d+"));
    }

    @Test
    @DisplayName("pas de nouvelle page tant que le palier a déjà toutes les siennes en file")
    void uneSeulePageEnFileParPalier() {
        when(crawler.active()).thenReturn(true);
        when(seeds.countByGroupAndSampledAtGreaterThanEqual(anyString(), any())).thenReturn(0L);
        when(tasks.countByTypeAndKeyStartingWithAndStateIn(eq(IngestTaskType.LADDER_PAGE), anyString(), anyCollection()))
                .thenReturn((long) properties.getSampling().getPagesInFlight());

        sampler.round();

        verify(queue, never()).enqueue(any(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("collecte de fond suspendue : l'échantillon aussi")
    void suitLaCollecteDeFond() {
        when(crawler.active()).thenReturn(false);

        sampler.round();

        verify(queue, never()).enqueue(any(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("une page lue verse tous ses rangs dans l'historique, et ne tire que les graines qui manquent")
    void tireLesGrainesManquantes() {
        List<RiotLeagueEntryResponse> page = IntStream.range(0, 205).mapToObj(i -> entree("p" + i, false)).toList();
        when(riotApiClient.ladderPage("RANKED_SOLO_5x5", "GOLD", "II", 3)).thenReturn(page);
        when(seeds.countByGroupAndSampledAtGreaterThanEqual(eq("GOLD"), any())).thenReturn(296L);
        when(seeds.findByPuuidInAndSampledAtGreaterThanEqual(anyCollection(), any())).thenReturn(List.of());

        sampler.samplePage("GOLD/II/3");

        ArgumentCaptor<List<RankHistory.Observation>> releves = ArgumentCaptor.forClass(List.class);
        verify(rankHistory).recordAll(releves.capture());
        assertThat(releves.getValue()).hasSize(205);
        verify(seeds, times(4)).save(any(LadderSeed.class));
        verify(queue, times(4)).enqueue(eq(IngestTaskType.SEED_MATCHES), anyString(), anyString(),
                eq(IngestTask.BACKGROUND_PLAYER_PRIORITY));
    }

    @Test
    @DisplayName("un compte inactif ou déjà tiré sur la fenêtre n'est pas retenu")
    void ecarteInactifsEtDejaTires() {
        when(riotApiClient.ladderPage("RANKED_SOLO_5x5", "GOLD", "II", 3))
                .thenReturn(List.of(entree("inactif", true), entree("deja", false), entree("neuf", false)));
        when(seeds.countByGroupAndSampledAtGreaterThanEqual(eq("GOLD"), any())).thenReturn(0L);
        when(seeds.findByPuuidInAndSampledAtGreaterThanEqual(anyCollection(), any()))
                .thenReturn(List.of(new LadderSeed("deja", "GOLD", "GOLD", "II", 0, MAINTENANT)));

        sampler.samplePage("GOLD/II/3");

        ArgumentCaptor<LadderSeed> graine = ArgumentCaptor.forClass(LadderSeed.class);
        verify(seeds).save(graine.capture());
        assertThat(graine.getValue().puuid()).isEqualTo("neuf");
    }

    @Test
    @DisplayName("une page vide borne le tirage de sa division")
    void unePageVideBorneLaDivision() {
        when(riotApiClient.ladderPage("RANKED_SOLO_5x5", "IRON", "I", 40)).thenReturn(List.of());
        when(bounds.findById("IRON/I")).thenReturn(Optional.empty());

        sampler.samplePage("IRON/I/40");

        verify(bounds).save(new LadderBound("IRON/I", 40));
        verify(seeds, never()).save(any());
    }

    @Test
    @DisplayName("une ligue au sommet se lit en un appel et reste dans le groupe Maître+")
    void litLeSommet() {
        when(riotApiClient.apexLeague("RANKED_SOLO_5x5", "CHALLENGER")).thenReturn(List.of(entree("c1", false)));
        when(seeds.countByGroupAndSampledAtGreaterThanEqual(eq("MASTER_PLUS"), any())).thenReturn(0L);
        when(seeds.findByPuuidInAndSampledAtGreaterThanEqual(anyCollection(), any())).thenReturn(List.of());

        sampler.samplePage("MASTER_PLUS/CHALLENGER");

        ArgumentCaptor<LadderSeed> graine = ArgumentCaptor.forClass(LadderSeed.class);
        verify(seeds).save(graine.capture());
        assertThat(graine.getValue().group()).isEqualTo("MASTER_PLUS");
    }

    @Test
    @DisplayName("les parties d'une graine reçoivent son rang, et chacune un résumé de timeline à collecter")
    void collecteLesPartiesDUneGraine() {
        when(seeds.findById("p1")).thenReturn(Optional.of(new LadderSeed("p1", "GOLD", "GOLD", "II", 40, MAINTENANT)));
        when(riotApiClient.rankedMatchIds("p1", 420, 20)).thenReturn(List.of("EUW1_1", "EUW1_2"));
        when(lobbies.findAllById(List.of("EUW1_1", "EUW1_2")))
                .thenReturn(List.of(new MatchLobby("EUW1_1", "autre", "SILVER", "I", MAINTENANT)));

        sampler.collectSeed("p1");

        ArgumentCaptor<List<MatchLobby>> nouvelles = ArgumentCaptor.forClass(List.class);
        verify(lobbies).saveAll(nouvelles.capture());
        assertThat(nouvelles.getValue()).extracting(MatchLobby::matchId, MatchLobby::tier)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EUW1_2", "GOLD"));
        verify(queue, times(2)).enqueue(eq(IngestTaskType.MATCH_TIMELINE_DIGEST), startsWith("EUW1_"), eq("p1"),
                anyLong());
    }

    private static RiotLeagueEntryResponse entree(String puuid, boolean inactif) {
        return new RiotLeagueEntryResponse(puuid, "RANKED_SOLO_5x5", "GOLD", "II", 40, 10, 10, false, false, false,
                inactif);
    }
}
