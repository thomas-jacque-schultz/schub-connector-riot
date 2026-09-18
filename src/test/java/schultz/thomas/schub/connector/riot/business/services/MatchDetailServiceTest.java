package schultz.thomas.schub.connector.riot.business.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailsResponse;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.mapper.MatchMapper;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.PlayerMatchRef;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotMatchResponse;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerMatchRefRepository;
import schultz.thomas.schub.connector.riot.support.Fixtures;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Politique n°1 : une partie terminée est récupérée une fois, et une seule.
 *
 * <p>C'est l'exigence qui justifie à elle seule que ce connecteur possède une base.</p>
 */
@ExtendWith(MockitoExtension.class)
class MatchDetailServiceTest {

    private static final String MATCH_ID = "EUW1_7987650481";

    @Mock private CachedMatchRepository matches;
    @Mock private PlayerMatchRefRepository playerMatches;
    @Mock private RiotApiClient riotApiClient;

    private MatchDetailService service;
    private RiotProperties properties;
    private MatchDetail partie;

    @BeforeEach
    void setUp() {
        properties = new RiotProperties();
        TestClock clock = new TestClock(Instant.parse("2026-09-18T12:00:00Z"));
        service = new MatchDetailService(matches, playerMatches, riotApiClient, new MatchMapper(),
                properties, clock);
        partie = new MatchMapper().toDomain(
                Fixtures.load("match-ranked-solo.json", RiotMatchResponse.class));
    }

    @Test
    @DisplayName("une partie déjà en base n'est JAMAIS redemandée à Riot")
    void neRedemandeJamaisUnePartieConnue() {
        when(matches.findById(MATCH_ID))
                .thenReturn(Optional.of(new CachedMatch(MATCH_ID, partie, Instant.now())));

        Optional<MatchDetail> resultat = service.detail(MATCH_ID);

        assertThat(resultat).contains(partie);
        // Le cœur de l'exigence : aucun appel sortant, aucun quota consommé.
        verify(riotApiClient, never()).match(anyString());
    }

    @Test
    @DisplayName("une partie inconnue est récupérée puis rangée pour toujours")
    void rangeUnePartieInconnue() {
        when(matches.findById(MATCH_ID)).thenReturn(Optional.empty());
        when(riotApiClient.match(MATCH_ID)).thenReturn(Optional.of(
                Fixtures.load("match-ranked-solo.json", RiotMatchResponse.class)));
        when(playerMatches.findByMatchIdIn(List.of(MATCH_ID))).thenReturn(List.of());

        Optional<MatchDetail> resultat = service.detail(MATCH_ID);

        assertThat(resultat).isPresent();
        assertThat(resultat.get().queueId()).isEqualTo(420);
        verify(matches).save(any(CachedMatch.class));
    }

    @Test
    @DisplayName("récupérer une partie date les renvois d'historique qui l'attendaient")
    void dateLesRenvoisEnAttente() {
        when(matches.findById(MATCH_ID)).thenReturn(Optional.empty());
        when(riotApiClient.match(MATCH_ID)).thenReturn(Optional.of(
                Fixtures.load("match-ranked-solo.json", RiotMatchResponse.class)));
        PlayerMatchRef sansDate = new PlayerMatchRef(
                PlayerMatchRef.idOf("p1", MATCH_ID), "p1", MATCH_ID, null, Instant.now());
        when(playerMatches.findByMatchIdIn(List.of(MATCH_ID))).thenReturn(List.of(sansDate));

        service.detail(MATCH_ID);

        // Sans cela, le renvoi resterait éternellement « à récupérer » et serait redemandé
        // à chaque synchronisation.
        verify(playerMatches).saveAll(List.of(sansDate.playedAt(partie.startedAt())));
    }

    @Test
    @DisplayName("un lot borne ses appels sortants et annonce ce qui reste")
    void borneLesAppelsEtAnnonceLeReste() {
        properties.getCache().setMaxDetailsPerCall(1);
        when(matches.findByMatchIdIn(any())).thenReturn(List.of());
        when(riotApiClient.match("EUW1_A")).thenReturn(Optional.of(
                Fixtures.load("match-ranked-solo.json", RiotMatchResponse.class)));
        when(playerMatches.findByMatchIdIn(any())).thenReturn(List.of());

        MatchDetailsResponse reponse = service.details(List.of("EUW1_A", "EUW1_B", "EUW1_C"));

        assertThat(reponse.matches()).hasSize(1);
        // Une réponse partielle annoncée vaut mieux qu'une requête qui pend cinq minutes.
        assertThat(reponse.pending()).containsExactly("EUW1_B", "EUW1_C");
        verify(riotApiClient, never()).match("EUW1_B");
    }

    @Test
    @DisplayName("ce que Riot ne connaît pas est dit « indisponible », pas « en attente »")
    void distingueIndisponibleEtEnAttente() {
        when(matches.findByMatchIdIn(any())).thenReturn(List.of());
        when(riotApiClient.match("EUW1_PURGEE")).thenReturn(Optional.empty());

        MatchDetailsResponse reponse = service.details(List.of("EUW1_PURGEE"));

        assertThat(reponse.unavailable()).containsExactly("EUW1_PURGEE");
        assertThat(reponse.pending()).isEmpty();
    }

    @Test
    @DisplayName("un lot ne redemande que ce qui manque")
    void neRedemandeQueCeQuiManque() {
        when(matches.findByMatchIdIn(any()))
                .thenReturn(List.of(new CachedMatch(MATCH_ID, partie, Instant.now())));

        MatchDetailsResponse reponse = service.details(List.of(MATCH_ID));

        assertThat(reponse.matches()).hasSize(1);
        assertThat(reponse.pending()).isEmpty();
        verify(riotApiClient, never()).match(anyString());
    }
}
