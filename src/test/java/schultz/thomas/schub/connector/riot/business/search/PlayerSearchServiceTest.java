package schultz.thomas.schub.connector.riot.business.search;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;
import schultz.thomas.schub.connector.riot.api.dto.PlayerSuggestion;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ce que la recherche retient, ce qu'elle écarte, et dans quel ordre elle propose. */
@ExtendWith(MockitoExtension.class)
class PlayerSearchServiceTest {

    private static final Instant HIER = Instant.parse("2026-09-20T18:00:00Z");
    private static final Instant AVANT_HIER = Instant.parse("2026-09-19T18:00:00Z");
    private static final Instant IL_Y_A_DEUX_ANS = Instant.parse("2024-09-20T18:00:00Z");

    @Mock private MongoTemplate mongo;

    private PlayerSearchService service;

    /** L'index rend les comptes ; les participations rendent leurs chiffres. */
    private void index(KnownAccount... comptes) {
        service = new PlayerSearchService(mongo);
        when(mongo.find(any(Query.class), eq(KnownAccount.class))).thenReturn(List.of(comptes));
        lenient().when(mongo.aggregate(any(Aggregation.class), eq(MatchParticipation.class),
                        eq(Document.class)))
                .thenReturn(new AggregationResults<>(new ArrayList<>(), new Document()));
    }

    private void participations(Document... lignes) {
        lenient().when(mongo.aggregate(any(Aggregation.class), eq(MatchParticipation.class),
                        eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(lignes), new Document()));
    }

    private static KnownAccount vuEnPartie(String puuid, String gameName, String tagLine,
                                           Instant observedAt) {
        return new KnownAccount(puuid, gameName, tagLine, SearchName.fold(gameName), observedAt,
                KnownAccountSource.PARTICIPATION);
    }

    private static KnownAccount verifie(String puuid, String gameName, String tagLine,
                                        Instant observedAt) {
        return new KnownAccount(puuid, gameName, tagLine, SearchName.fold(gameName), observedAt,
                KnownAccountSource.RESOLUTION);
    }

    private static Document compteurs(String puuid, int parties, String... positions) {
        return new Document("_id", puuid)
                .append("matchCount", parties)
                .append("lastPlayedAt", Date.from(HIER))
                .append("positions", List.of(positions));
    }

    @Test
    @DisplayName("aucun compte ne ressemble : liste vide, et surtout pas une erreur")
    void rendUneListeVideQuandRienNeRessemble() {
        index();

        assertThat(service.search("personne", 10)).isEmpty();
    }

    @Test
    @DisplayName("une saisie vide ne déclenche aucune requête")
    void neCherchePasSurUneSaisieVide() {
        service = new PlayerSearchService(mongo);

        assertThat(service.search("   ", 10)).isEmpty();
        verify(mongo, never()).find(any(Query.class), eq(KnownAccount.class));
    }

    @Test
    @DisplayName("le compte exact passe devant le plus vu")
    void classeLExactAvantLePlusVu() {
        index(vuEnPartie("p-long", "Thomasson", "EUW", HIER),
                vuEnPartie("p-exact", "Thomas", "EUW", HIER));
        participations(compteurs("p-long", 900, "TOP"), compteurs("p-exact", 12, "MIDDLE"));

        assertThat(service.search("thomas", 10)).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-exact", "p-long");
    }

    @Test
    @DisplayName("accents et casse sont ignorés des deux côtés de la comparaison")
    void ignoreLesAccentsEtLaCasse() {
        index(vuEnPartie("p1", "Rémi Le Grand", "EUW", HIER));
        participations(compteurs("p1", 30, "UTILITY"));

        assertThat(service.search("REMILE", 10)).extracting(PlayerSuggestion::puuid).containsExactly("p1");
    }

    @Test
    @DisplayName("une faute de frappe est rattrapée, un pseudo simplement voisin ne l'est pas")
    void toleUneFauteDeFrappeEtPasPlus() {
        index(vuEnPartie("p-faute", "Thomas", "EUW", HIER),
                vuEnPartie("p-autre", "Thoreau", "EUW", HIER));
        participations(compteurs("p-faute", 30), compteurs("p-autre", 30));

        assertThat(service.search("thomsa", 10)).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-faute");
    }

    @Test
    @DisplayName("un Riot ID collé entier filtre les homonymes par le tag")
    void filtreParLeTagQuandIlEstDonne() {
        index(vuEnPartie("p-euw", "Thomas", "EUW", HIER), vuEnPartie("p-fr", "Thomas", "FR1", HIER));
        participations(compteurs("p-euw", 30), compteurs("p-fr", 30));

        assertThat(service.search("Thomas#FR1", 10)).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-fr");
    }

    @Test
    @DisplayName("les postes rendus sont les plus joués, le poste inconnu n'en est pas un")
    void classeLesPostesLesPlusJoues() {
        index(vuEnPartie("p1", "Thomas", "EUW", HIER));
        participations(compteurs("p1", 5, "TOP", "MIDDLE", "MIDDLE", "UNKNOWN", "UNKNOWN"));

        List<PlayerSuggestion.PositionPlayed> postes = service.search("thomas", 10).getFirst().positions();

        assertThat(postes).extracting(PlayerSuggestion.PositionPlayed::position)
                .containsExactly(TeamPosition.MIDDLE, TeamPosition.TOP);
    }

    @Test
    @DisplayName("la dernière partie vue est rendue telle quelle, c'est ce qui fait reconnaître son compte")
    void rendLaDernierePartieVue() {
        index(vuEnPartie("p1", "Thomas", "EUW", HIER));
        participations(compteurs("p1", 5, "TOP"));

        PlayerSuggestion proposition = service.search("thomas", 10).getFirst();

        assertThat(proposition.lastPlayedAt()).isEqualTo(HIER);
        assertThat(proposition.riotId()).isEqualTo("Thomas#EUW");
        assertThat(proposition.matchCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("un compte vérifié et jamais croisé en partie est proposé quand même")
    void proposeUnCompteVerifieSansAucunePartie() {
        index(verifie("p-neuf", "Thomas", "EUW", HIER));

        PlayerSuggestion proposition = service.search("Thomas#EUW", 10).getFirst();

        assertThat(proposition.puuid()).isEqualTo("p-neuf");
        assertThat(proposition.matchCount()).isZero();
        assertThat(proposition.positions()).isEmpty();
        assertThat(proposition.lastPlayedAt()).isNull();
        assertThat(proposition.observedAt()).isEqualTo(HIER);
        assertThat(proposition.source()).isEqualTo(KnownAccountSource.RESOLUTION);
    }

    @Test
    @DisplayName("deux entrées pour un même Riot ID : seule la plus récemment observée est proposée")
    void necarteLEntreePerimeeQuandLeRiotIdAChangeDeMain() {
        index(vuEnPartie("p-ancien", "Thomas", "EUW", IL_Y_A_DEUX_ANS),
                verifie("p-actuel", "Thomas", "EUW", HIER));
        participations(compteurs("p-ancien", 900, "TOP"));

        assertThat(service.search("Thomas#EUW", 10)).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-actuel");
    }

    @Test
    @DisplayName("à ressemblance et parties égales, le plus récemment observé passe devant")
    void departageParLaFraicheurDeLObservation() {
        index(vuEnPartie("p-vieux", "Thomas", "EUW", AVANT_HIER),
                vuEnPartie("p-frais", "Thomas", "FR1", HIER));
        participations(compteurs("p-vieux", 10), compteurs("p-frais", 10));

        assertThat(service.search("thomas", 10)).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-frais", "p-vieux");
    }
}
