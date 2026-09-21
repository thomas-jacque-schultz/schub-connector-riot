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

import schultz.thomas.schub.connector.riot.api.dto.PlayerSuggestion;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ce que la recherche retient, ce qu'elle écarte, et dans quel ordre elle propose. */
@ExtendWith(MockitoExtension.class)
class PlayerSearchServiceTest {

    private static final Instant HIER = Instant.parse("2026-09-20T18:00:00Z");

    @Mock private MongoTemplate mongo;

    private PlayerSearchService service;

    private void base(Document... rows) {
        service = new PlayerSearchService(mongo);
        when(mongo.aggregate(any(Aggregation.class), eq(MatchParticipation.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(rows), new Document()));
    }

    private static Document joueur(String puuid, String gameName, String tagLine, int parties,
                                   String... positions) {
        return new Document("_id", puuid)
                .append("gameName", gameName)
                .append("tagLine", tagLine)
                .append("searchName", SearchName.fold(gameName))
                .append("matchCount", parties)
                .append("lastPlayedAt", Date.from(HIER))
                .append("positions", List.of(positions));
    }

    @Test
    @DisplayName("aucun compte ne ressemble : liste vide, et surtout pas une erreur")
    void rendUneListeVideQuandRienNeRessemble() {
        base();

        assertThat(service.search("personne", 10)).isEmpty();
    }

    @Test
    @DisplayName("une saisie vide ne déclenche aucune requête")
    void neCherchePasSurUneSaisieVide() {
        service = new PlayerSearchService(mongo);

        assertThat(service.search("   ", 10)).isEmpty();
        verify(mongo, never()).aggregate(any(Aggregation.class), eq(MatchParticipation.class),
                eq(Document.class));
    }

    @Test
    @DisplayName("le compte exact passe devant le plus vu")
    void classeLExactAvantLePlusVu() {
        base(joueur("p-long", "Thomasson", "EUW", 900, "TOP"),
                joueur("p-exact", "Thomas", "EUW", 12, "MIDDLE"));

        List<PlayerSuggestion> propositions = service.search("thomas", 10);

        assertThat(propositions).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-exact", "p-long");
    }

    @Test
    @DisplayName("accents et casse sont ignorés des deux côtés de la comparaison")
    void ignoreLesAccentsEtLaCasse() {
        base(joueur("p1", "Rémi Le Grand", "EUW", 30, "UTILITY"));

        assertThat(service.search("REMILE", 10)).extracting(PlayerSuggestion::puuid).containsExactly("p1");
    }

    @Test
    @DisplayName("une faute de frappe est rattrapée, un pseudo simplement voisin ne l'est pas")
    void toleUneFauteDeFrappeEtPasPlus() {
        base(joueur("p-faute", "Thomas", "EUW", 30), joueur("p-autre", "Thoreau", "EUW", 30));

        assertThat(service.search("thomsa", 10)).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-faute");
    }

    @Test
    @DisplayName("un Riot ID collé entier filtre les homonymes par le tag")
    void filtreParLeTagQuandIlEstDonne() {
        base(joueur("p-euw", "Thomas", "EUW", 30), joueur("p-fr", "Thomas", "FR1", 30));

        assertThat(service.search("Thomas#FR1", 10)).extracting(PlayerSuggestion::puuid)
                .containsExactly("p-fr");
    }

    @Test
    @DisplayName("les postes rendus sont les plus joués, le poste inconnu n'en est pas un")
    void classeLesPostesLesPlusJoues() {
        base(joueur("p1", "Thomas", "EUW", 5, "TOP", "MIDDLE", "MIDDLE", "UNKNOWN", "UNKNOWN"));

        List<PlayerSuggestion.PositionPlayed> postes = service.search("thomas", 10).getFirst().positions();

        assertThat(postes).extracting(PlayerSuggestion.PositionPlayed::position)
                .containsExactly(TeamPosition.MIDDLE, TeamPosition.TOP);
    }

    @Test
    @DisplayName("la dernière partie vue est rendue telle quelle, c'est ce qui fait reconnaître son compte")
    void rendLaDernierePartieVue() {
        base(joueur("p1", "Thomas", "EUW", 5, "TOP"));

        PlayerSuggestion proposition = service.search("thomas", 10).getFirst();

        assertThat(proposition.lastPlayedAt()).isEqualTo(HIER);
        assertThat(proposition.riotId()).isEqualTo("Thomas#EUW");
        assertThat(proposition.matchCount()).isEqualTo(5);
    }
}
