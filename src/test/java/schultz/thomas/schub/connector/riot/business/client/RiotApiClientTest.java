package schultz.thomas.schub.connector.riot.business.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.quota.RiotRateLimiter;
import schultz.thomas.schub.connector.riot.config.RiotApiConfiguration;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotAccountResponse;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Le client, face à un serveur simulé : aucun réseau, aucune clé qui expire. */
class RiotApiClientTest {

    private static final String PUUID = "puuid-de-test";

    private RiotProperties properties;
    private TestClock clock;
    private RiotApiClient client;
    private MockRestServiceServer regionalServer;
    private MockRestServiceServer platformServer;

    @BeforeEach
    void setUp() {
        properties = new RiotProperties();
        properties.setApiKey("RGAPI-clef-de-test");
        clock = new TestClock(Instant.parse("2026-09-18T12:00:00Z"));

        // Les clients sont montés par le chemin de production : l'en-tête de clé et le choix
        // de l'hôte sont ainsi réellement couverts, et non reconstitués par le test.
        RiotApiConfiguration configuration = new RiotApiConfiguration(properties);
        RestClient.Builder regional =
                configuration.riotDefaults(RestClient.builder(), properties.regionalBaseUrl());
        RestClient.Builder platform =
                configuration.riotDefaults(RestClient.builder(), properties.platformBaseUrl());
        regionalServer = MockRestServiceServer.bindTo(regional).build();
        platformServer = MockRestServiceServer.bindTo(platform).build();

        RiotRateLimiter limiteur = new RiotRateLimiter(properties.getQuota(), clock,
                clock::advance);
        client = new RiotApiClient(regional.build(), platform.build(), limiteur, properties);
    }

    @Test
    @DisplayName("account-v1 part sur la route régionale, jamais sur la plateforme")
    void resoutUnRiotIdSurLaRouteRegionale() {
        regionalServer.expect(requestTo(
                        "https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/J1HUIV/000"))
                .andExpect(header("X-Riot-Token", "RGAPI-clef-de-test"))
                .andRespond(withSuccess("""
                        {"puuid":"abc","gameName":"J1HUIV","tagLine":"000"}""",
                        MediaType.APPLICATION_JSON));

        Optional<RiotAccountResponse> compte = client.accountByRiotId("J1HUIV", "000");

        assertThat(compte).isPresent();
        assertThat(compte.get().puuid()).isEqualTo("abc");
        regionalServer.verify();
        platformServer.verify();
    }

    @Test
    @DisplayName("league-v4 part sur la route plateforme, jamais sur la régionale")
    void releveLeClassementSurLaRoutePlateforme() {
        platformServer.expect(requestTo(
                        "https://euw1.api.riotgames.com/lol/league/v4/entries/by-puuid/" + PUUID))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.leagueEntries(PUUID)).isEmpty();
        platformServer.verify();
    }

    @Test
    @DisplayName("startTime part en SECONDES, là où le reste de l'API Riot est en millisecondes")
    void envoieStartTimeEnSecondes() {
        Instant depuis = Instant.parse("2026-09-01T00:00:00Z");
        regionalServer.expect(requestTo(
                        "https://europe.api.riotgames.com/lol/match/v5/matches/by-puuid/" + PUUID
                                + "/ids?start=0&count=100&startTime=" + depuis.getEpochSecond()))
                .andRespond(withSuccess("[\"EUW1_1\",\"EUW1_2\"]", MediaType.APPLICATION_JSON));

        assertThat(client.matchIds(PUUID, depuis, 0, 100)).containsExactly("EUW1_1", "EUW1_2");
        regionalServer.verify();
    }

    @Test
    @DisplayName("un 404 est une absence, pas une panne")
    void traiteUn404CommeUneAbsence() {
        regionalServer.expect(requestTo(
                        "https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Inconnu/XXX"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.accountByRiotId("Inconnu", "XXX")).isEmpty();
    }

    @Test
    @DisplayName("un 429 est une instruction : on attend le Retry-After, puis on reprend")
    void respecteLeRetryAfterPuisReprend() {
        HttpHeaders entetes = new HttpHeaders();
        entetes.add(HttpHeaders.RETRY_AFTER, "2");

        regionalServer.expect(requestTo(
                        "https://europe.api.riotgames.com/riot/account/v1/accounts/by-puuid/" + PUUID))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(entetes));
        regionalServer.expect(requestTo(
                        "https://europe.api.riotgames.com/riot/account/v1/accounts/by-puuid/" + PUUID))
                .andRespond(withSuccess("""
                        {"puuid":"puuid-de-test","gameName":"J1HUIV","tagLine":"000"}""",
                        MediaType.APPLICATION_JSON));

        Instant avant = clock.instant();
        Optional<RiotAccountResponse> compte = client.accountByPuuid(PUUID);

        assertThat(compte).isPresent();
        // Le temps a avancé d'au moins les deux secondes demandées : la reprise n'est pas
        // immédiate. Réessayer aussitôt empirerait la situation — un 429 consomme du quota.
        assertThat(Duration.between(avant, clock.instant()))
                .isGreaterThanOrEqualTo(Duration.ofSeconds(2));
        regionalServer.verify();
    }

    @Test
    @DisplayName("un 403 nomme la méprise de routage plutôt que de laisser chercher")
    void nommeLaMepriseDeRoutage() {
        regionalServer.expect(requestTo(
                        "https://europe.api.riotgames.com/lol/match/v5/matches/EUW1_1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.match("EUW1_1"))
                .isInstanceOf(RiotApiException.class)
                .hasMessageContaining("régional vs plateforme");
    }

    @Test
    @DisplayName("sans clé, aucune requête n'est émise — le connecteur est en veille")
    void nEmetRienSansCle() {
        properties.setApiKey("");

        assertThatThrownBy(() -> client.accountByPuuid(PUUID))
                .isInstanceOf(RiotKeyMissingException.class);

        // Aucune attente enregistrée sur le serveur simulé : rien n'est parti.
        regionalServer.verify();
        assertThat(List.of()).isEmpty();
    }
}
