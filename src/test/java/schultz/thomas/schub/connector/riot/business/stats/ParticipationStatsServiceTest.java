package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import schultz.thomas.schub.connector.riot.api.dto.ParticipationBucket;
import schultz.thomas.schub.connector.riot.api.dto.PlayerCoverage;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatches;
import schultz.thomas.schub.connector.riot.api.dto.StatsGrouping;
import schultz.thomas.schub.connector.riot.api.dto.StatsScope;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerHistoryCursorRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerMatchRefRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipationStatsServiceTest {

    private static final Instant QUAND = Instant.parse("2026-09-20T18:00:00Z");

    @Mock private MongoTemplate mongo;
    @Mock private MatchParticipationRepository participations;
    @Mock private PlayerMatchRefRepository matchRefs;
    @Mock private PlayerHistoryCursorRepository cursors;

    private ParticipationStatsService service() {
        return new ParticipationStatsService(mongo, participations, matchRefs, cursors);
    }

    private void rows(Document... lignes) {
        lenient().when(mongo.aggregate(any(Aggregation.class), eq(MatchParticipation.class),
                        eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(lignes), new Document()));
    }

    private static Document groupe(String puuid, String cle, long games, long wins) {
        return new Document("_id", new Document("puuid", puuid).append("statsKey", cle))
                .append("games", games)
                .append("wins", wins)
                .append("kills", 10L)
                .append("deaths", 4L)
                .append("assists", 8L)
                .append("secondsPlayed", 1800L)
                .append("firstPlayedAt", Date.from(QUAND))
                .append("lastPlayedAt", Date.from(QUAND));
    }

    private static MatchParticipation participation(String puuid, String matchId, boolean win, int side) {
        return new MatchParticipation(MatchParticipation.idOf(puuid, matchId), puuid, matchId,
                "Pseudo", "TAG", "pseudo", 126, "Jayce", TeamPosition.MIDDLE, win, side, 1800,
                440, QueueKind.RANKED_FLEX, "16.18.1.1", "16.18", "EUW1", QUAND, true,
                5, 2, 3, 150, 12000, 20000, 18000, 25, 20, 18, false,
                null, null, null, MatchParticipation.PROJECTION_VERSION, QUAND);
    }

    @Test
    @DisplayName("Aucun puuid : aucune requête, pas une agrégation sur toute la collection")
    void aucunPuuid() {
        assertThat(service().aggregate(Arrays.asList("  ", null), StatsGrouping.OVERALL, StatsScope.ALL, null)).isEmpty();
        verify(mongo, never()).aggregate(any(Aggregation.class), eq(MatchParticipation.class),
                eq(Document.class));
    }

    @Test
    @DisplayName("La clé de groupe est bien celle de l'axe demandé")
    void cleDeGroupe() {
        rows();
        service().aggregate(List.of("p1"), StatsGrouping.MONTH, StatsScope.ALL, null);

        ArgumentCaptor<Aggregation> capture = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(capture.capture(), eq(MatchParticipation.class), eq(Document.class));
        assertThat(capture.getValue().toString()).contains("$dateToString").contains("%Y-%m");
    }

    @Test
    @DisplayName("La Faille ne garde que ses files, et le poste inconnu n'est jamais un groupe")
    void failleEtPosteInconnu() {
        rows();
        service().aggregate(List.of("p1"), StatsGrouping.POSITION, StatsScope.RIFT, null);

        ArgumentCaptor<Aggregation> capture = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(capture.capture(), eq(MatchParticipation.class), eq(Document.class));
        assertThat(capture.getValue().toString())
                .contains("queueId").contains("420").contains("$nin").contains("UNKNOWN");
    }

    @Test
    @DisplayName("Une partie partagée rend les dix joueurs, et dit lesquels ont été demandés")
    void dixJoueurs() {
        when(mongo.aggregate(any(Aggregation.class), eq(MatchParticipation.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(
                        List.of(new Document("_id", "EUW1_2").append("present", 1)), new Document()));
        when(participations.findByMatchIdIn(List.of("EUW1_2"))).thenReturn(List.of(
                participation("p1", "EUW1_2", true, 100),
                participation("x1", "EUW1_2", true, 100),
                participation("x2", "EUW1_2", false, 200)));

        SharedMatches communes = service().sharedMatches(List.of("p1"), 1, null, null);

        assertThat(communes.matches()).singleElement().satisfies(partie -> {
            assertThat(partie.presentPlayers()).isEqualTo(1);
            assertThat(partie.players()).hasSize(3);
            assertThat(partie.players()).filteredOn(joueur -> joueur.requested())
                    .singleElement().satisfies(joueur -> assertThat(joueur.puuid()).isEqualTo("p1"));
        });
    }

    @Test
    @DisplayName("Les groupes rendus portent des sommes, et la clé telle quelle")
    void sommes() {
        rows(groupe("p1", "126", 40, 22));
        List<ParticipationBucket> buckets = service().aggregate(List.of("p1"),
                StatsGrouping.CHAMPION, StatsScope.ALL, null);

        assertThat(buckets).singleElement().satisfies(bucket -> {
            assertThat(bucket.key()).isEqualTo("126");
            assertThat(bucket.games()).isEqualTo(40);
            assertThat(bucket.wins()).isEqualTo(22);
            assertThat(bucket.firstPlayedAt()).isEqualTo(QUAND);
        });
    }

    @Test
    @DisplayName("Les files sont rendues par mode de jeu, et les identifiants d'un même mode s'additionnent")
    void filesReplieesParMode() {
        rows(groupe("p1", "1700", 3, 2), groupe("p1", "1710", 2, 1), groupe("p1", "420", 10, 5));
        List<ParticipationBucket> buckets = service().aggregate(List.of("p1"),
                StatsGrouping.QUEUE, StatsScope.ALL, null);

        assertThat(buckets).extracting(ParticipationBucket::key)
                .containsExactly("RANKED_SOLO", "ARENA");
        assertThat(buckets).filteredOn(bucket -> "ARENA".equals(bucket.key()))
                .singleElement()
                .satisfies(arene -> {
                    assertThat(arene.games()).isEqualTo(5);
                    assertThat(arene.wins()).isEqualTo(3);
                    assertThat(arene.secondsPlayed()).isEqualTo(3600);
                });
    }

    @Test
    @DisplayName("Une file que Riot vient d'ajouter reste lisible au lieu de porter son numéro")
    void fileInconnueLisible() {
        rows(groupe("p1", "9999", 4, 2));

        assertThat(service().aggregate(List.of("p1"), StatsGrouping.QUEUE, StatsScope.ALL, null))
                .singleElement()
                .satisfies(bucket -> assertThat(bucket.key()).isEqualTo("OTHER"));
    }

    @Test
    @DisplayName("Un effectif plus petit que le seuil ne peut produire aucune partie commune")
    void seuilInatteignable() {
        SharedMatches communes = service().sharedMatches(List.of("p1", "p2"), 4, null, null);

        assertThat(communes.matches()).isEmpty();
        assertThat(communes.totalMatches()).isZero();
        verify(mongo, never()).aggregate(any(Aggregation.class), eq(MatchParticipation.class),
                eq(Document.class));
    }

    @Test
    @DisplayName("Les joueurs demandés séparés en deux camps : aucun résultat commun")
    void camposOpposes() {
        when(mongo.aggregate(any(Aggregation.class), eq(MatchParticipation.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(
                        List.of(new Document("_id", "EUW1_1").append("present", 4)), new Document()));
        when(participations.findByMatchIdIn(List.of("EUW1_1"))).thenReturn(List.of(
                participation("p1", "EUW1_1", true, 100),
                participation("p2", "EUW1_1", true, 100),
                participation("p3", "EUW1_1", false, 200),
                participation("p4", "EUW1_1", false, 200)));

        SharedMatches communes = service()
                .sharedMatches(List.of("p1", "p2", "p3", "p4", "p5"), 4, null, null);

        assertThat(communes.matches()).singleElement().satisfies(partie -> {
            assertThat(partie.splitSides()).isTrue();
            assertThat(partie.win()).isNull();
            assertThat(partie.presentPlayers()).isEqualTo(4);
            assertThat(partie.queueId()).isEqualTo(440);
        });
    }

    @Test
    @DisplayName("Un compte jamais collecté a des parties sans être suivi : la couverture le dit")
    void couvertureDUnCompteNonSuivi() {
        rows(groupe("p1", "", 12, 6));
        when(cursors.findAllById(List.of("p1"))).thenReturn(List.of());
        when(matchRefs.countByPuuid(anyString())).thenReturn(0L);

        List<PlayerCoverage> couverture = service().coverage(List.of("p1"));

        assertThat(couverture).singleElement().satisfies(ligne -> {
            assertThat(ligne.tracked()).isFalse();
            assertThat(ligne.analysedMatches()).isEqualTo(12);
            assertThat(ligne.knownMatches()).isZero();
        });
    }

    @Test
    @DisplayName("Un compte suivi mais sans partie : zéro, et les dates de collecte quand même")
    void couvertureSansPartie() {
        when(mongo.aggregate(any(Aggregation.class), eq(MatchParticipation.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(new ArrayList<>(), new Document()));
        when(cursors.findAllById(List.of("p1")))
                .thenReturn(List.of(new PlayerHistoryCursor("p1", QUAND, QUAND, null)));
        when(matchRefs.countByPuuid("p1")).thenReturn(3L);

        List<PlayerCoverage> couverture = service().coverage(List.of("p1"));

        assertThat(couverture).singleElement().satisfies(ligne -> {
            assertThat(ligne.tracked()).isTrue();
            assertThat(ligne.analysedMatches()).isZero();
            assertThat(ligne.knownMatches()).isEqualTo(3);
            assertThat(ligne.firstPlayedAt()).isNull();
            assertThat(ligne.lastSyncAt()).isEqualTo(QUAND);
        });
    }
}
