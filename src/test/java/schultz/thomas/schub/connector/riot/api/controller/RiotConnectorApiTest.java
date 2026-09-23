package schultz.thomas.schub.connector.riot.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCatalog;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailsResponse;
import schultz.thomas.schub.connector.riot.api.dto.MatchHistory;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIngestStatus;
import schultz.thomas.schub.connector.riot.api.dto.PlayerSuggestion;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotConnectorBusyException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.ingest.BackgroundCrawler;
import schultz.thomas.schub.connector.riot.business.ingest.LadderSampler;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.business.search.PlayerSearchService;
import schultz.thomas.schub.connector.riot.business.services.ChampionCatalogService;
import schultz.thomas.schub.connector.riot.business.services.ChampionMasteryService;
import schultz.thomas.schub.connector.riot.business.services.MatchDetailService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.business.services.PlayerIdentityService;
import schultz.thomas.schub.connector.riot.business.services.RankingService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RiotConnectorApiTest {

    private static final String PUUID = "puuid-de-test";

    @Mock private PlayerIdentityService identityService;
    @Mock private MatchHistoryService historyService;
    @Mock private RankingService rankingService;
    @Mock private ChampionMasteryService masteryService;
    @Mock private MatchDetailService matchDetailService;
    @Mock private ChampionCatalogService catalogService;
    @Mock private IngestService ingestService;
    @Mock private ParticipationProjector projector;
    @Mock private PlayerSearchService playerSearchService;
    @Mock private KnownAccountIndex knownAccounts;
    @Mock private BackgroundCrawler crawler;
    @Mock private LadderSampler sampler;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PlayerController(identityService, historyService, rankingService,
                                masteryService, ingestService, playerSearchService),
                        new IngestController(ingestService, projector, knownAccounts, crawler, sampler),
                        new MatchController(matchDetailService),
                        new ChampionCatalogController(catalogService))
                .setControllerAdvice(new RiotExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /players résout un Riot ID en puuid")
    void resoutUnRiotId() throws Exception {
        when(identityService.resolve("J1HUIV", "000"))
                .thenReturn(new PlayerIdentity("abc", "J1HUIV", "000"));

        mockMvc.perform(get("/players").param("gameName", "J1HUIV").param("tagLine", "000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puuid").value("abc"))
                .andExpect(jsonPath("$.gameName").value("J1HUIV"));
    }

    @Test
    @DisplayName("GET /players/{puuid}/matches accepte une borne ISO-8601")
    void rendLHistoriqueDepuisUneDate() throws Exception {
        Instant depuis = Instant.parse("2026-09-01T00:00:00Z");
        when(historyService.history(eq(PUUID), eq(depuis))).thenReturn(
                new MatchHistory(PUUID, depuis, List.of("EUW1_1", "EUW1_2"), depuis, true));

        mockMvc.perform(get("/players/{puuid}/matches", PUUID)
                        .param("since", "2026-09-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchIds.length()").value(2))
                .andExpect(jsonPath("$.ingestQueued").value(true));
    }

    @Test
    @DisplayName("POST /matches/by-ids refuse une liste vide plutôt que d'appeler Riot pour rien")
    void refuseUnLotVide() throws Exception {
        mockMvc.perform(post("/matches/by-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /matches/by-ids annonce ce qui reste à récupérer")
    void annonceCeQuiResteARecuperer() throws Exception {
        when(matchDetailService.details(List.of("EUW1_1", "EUW1_2")))
                .thenReturn(new MatchDetailsResponse(List.of(), List.of("EUW1_2"), List.of()));

        mockMvc.perform(post("/matches/by-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchIds\":[\"EUW1_1\",\"EUW1_2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending[0]").value("EUW1_2"));
    }

    @Test
    @DisplayName("GET /champions rend toujours la version, même quand elle n'est pas demandée")
    void rendToujoursLaVersion() throws Exception {
        when(catalogService.catalog(null))
                .thenReturn(new ChampionCatalog("16.18.1", "fr_FR", List.of()));

        mockMvc.perform(get("/champions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("16.18.1"));
    }

    @Test
    @DisplayName("une partie inconnue de Riot donne un 404, pas un 500")
    void unePartieInconnueDonneUn404() throws Exception {
        when(matchDetailService.detail("EUW1_INCONNUE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/matches/{id}", "EUW1_INCONNUE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("le quota épuisé donne un 429 et répercute le Retry-After de Riot")
    void leQuotaEpuiseRepercuteLeRetryAfter() throws Exception {
        when(rankingService.rankings(anyString()))
                .thenThrow(new RiotQuotaExceededException("saturé", Duration.ofSeconds(2)));

        mockMvc.perform(get("/players/{puuid}/rankings", PUUID))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"));
    }

    @Test
    @DisplayName("un connecteur occupé se distingue d'une panne : 429 titré, jamais 502 ni 503")
    void unConnecteurOccupeSeDistingueDUnePanne() throws Exception {
        when(identityService.resolve("Pikachu", "STORM"))
                .thenThrow(new RiotConnectorBusyException("Connecteur occupé : pas de créneau",
                        Duration.ofSeconds(3)));

        mockMvc.perform(get("/players").param("gameName", "Pikachu").param("tagLine", "STORM"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.title").value("Connecteur Riot occupé"));
    }

    @Test
    @DisplayName("une panne de Riot donne un 502 : la panne est en amont")
    void unePanneDeRiotDonneUn502() throws Exception {
        when(identityService.identify(anyString())).thenThrow(new RiotApiException("Riot muet"));

        mockMvc.perform(get("/players/{puuid}", PUUID))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("une clé absente donne un 503 : ce n'est pas une panne, c'est une configuration")
    void uneCleAbsenteDonneUn503() throws Exception {
        when(masteryService.masteries(anyString(), any()))
                .thenThrow(new RiotKeyMissingException("aucune clé"));

        mockMvc.perform(get("/players/{puuid}/champion-mastery", PUUID))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("un Riot ID inexistant donne un 404")
    void unRiotIdInexistantDonneUn404() throws Exception {
        when(identityService.resolve(anyString(), anyString()))
                .thenThrow(new RiotResourceNotFoundException("inconnu"));

        mockMvc.perform(get("/players").param("gameName", "Personne").param("tagLine", "ZZZ"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /players/search propose des comptes connus de nos participations")
    void chercheDansNosParticipations() throws Exception {
        when(playerSearchService.search("thom", 10)).thenReturn(List.of(
                new PlayerSuggestion(PUUID, "Thomas", "EUW", "Thomas#EUW", 42,
                        List.of(new PlayerSuggestion.PositionPlayed(TeamPosition.MIDDLE, 30)),
                        Instant.parse("2026-09-20T18:00:00Z"),
                        Instant.parse("2026-09-20T18:00:00Z"), KnownAccountSource.PARTICIPATION)));

        mockMvc.perform(get("/players/search").param("q", "thom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].riotId").value("Thomas#EUW"))
                .andExpect(jsonPath("$[0].matchCount").value(42))
                .andExpect(jsonPath("$[0].positions[0].position").value("MIDDLE"));
    }

    @Test
    @DisplayName("GET /players/search sans résultat rend une liste vide, pas une erreur")
    void rendUneListeVideSansResultat() throws Exception {
        when(playerSearchService.search("zzz", 10)).thenReturn(List.of());

        mockMvc.perform(get("/players/search").param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /ingest/players/{puuid} donne l'avancement de ce joueur seul")
    void rendLAvancementDUnJoueur() throws Exception {
        when(ingestService.statusOf(PUUID)).thenReturn(new PlayerIngestStatus(
                PUUID, 200, 1, 0, 980, 49.0, Duration.ofMinutes(20),
                Instant.parse("2026-09-21T10:20:00Z")));

        mockMvc.perform(get("/ingest/players/{puuid}", PUUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(200))
                .andExpect(jsonPath("$.queuedAhead").value(980))
                .andExpect(jsonPath("$.estimatedReadyAt").exists());
    }
}
