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
import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
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

/**
 * Le contrat exposé : des routes en vocabulaire de domaine, et quatre échecs distincts.
 *
 * <p>Monté hors contexte Spring : ces tests vérifient le contrat HTTP, pas le câblage — et
 * surtout pas Mongo, qu'il faudrait sinon démarrer pour vérifier un code de statut.</p>
 */
@ExtendWith(MockitoExtension.class)
class RiotConnectorApiTest {

    private static final String PUUID = "puuid-de-test";

    @Mock private PlayerIdentityService identityService;
    @Mock private MatchHistoryService historyService;
    @Mock private RankingService rankingService;
    @Mock private ChampionMasteryService masteryService;
    @Mock private MatchDetailService matchDetailService;
    @Mock private ChampionCatalogService catalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PlayerController(identityService, historyService, rankingService,
                                masteryService),
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
                .andExpect(jsonPath("$.refreshed").value(true));
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

        // Taire le Retry-After ferait réessayer immédiatement, ce qui empire la situation :
        // les requêtes refusées comptent elles aussi dans le quota.
        mockMvc.perform(get("/players/{puuid}/rankings", PUUID))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"));
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
}
