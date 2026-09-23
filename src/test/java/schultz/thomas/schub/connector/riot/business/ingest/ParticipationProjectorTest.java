package schultz.thomas.schub.connector.riot.business.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.api.dto.RebuildReport;
import schultz.thomas.schub.connector.riot.business.mapper.MatchMapper;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchEarlyStatsRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchLobbyRepository;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchLobby;
import schultz.thomas.schub.connector.riot.data.model.RankSpan;
import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.support.Fixtures;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipationProjectorTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-21T10:00:00Z");

    @Mock private CachedMatchRepository matches;
    @Mock private MatchParticipationRepository participations;
    @Mock private KnownAccountIndex knownAccounts;
    @Mock private MetricScaleService metricScale;
    @Mock private MongoTemplate mongo;
    @Mock private MatchEarlyStatsRepository earlyStats;
    @Mock private RankHistory rankHistory;
    @Mock private MatchLobbyRepository lobbies;

    private ParticipationProjector projector;

    @BeforeEach
    void setUp() {
        projector = new ParticipationProjector(matches, participations, knownAccounts,
                new RawMatchDecoder(new MatchMapper()), new TestClock(MAINTENANT), metricScale, mongo, earlyStats, rankHistory, lobbies);
    }

    @Test
    @DisplayName("une partie donne une ligne par participant, pas seulement pour le demandeur")
    void projetteLesDixParticipants() {
        projector.project(new CachedMatch("EUW1_7987650481",
                Fixtures.document("match-ranked-solo.json"), MAINTENANT));

        assertThat(capture()).hasSize(10);
    }

    @Test
    @DisplayName("le Riot ID de chaque participant est projeté, replié pour la recherche")
    void projetteLesRiotIdDesParticipants() {
        projector.project(new CachedMatch("EUW1_7987650481",
                Fixtures.document("match-ranked-solo.json"), MAINTENANT));

        assertThat(capture()).extracting(MatchParticipation::riotId)
                .contains("Call me Izo#igi", "J1HUIV#000");
        assertThat(capture()).extracting(MatchParticipation::searchName)
                .contains("callmeizo", "j1huiv");
    }

    @Test
    @DisplayName("le côté, la file et le patch sont aplatis : ce sont les axes des statistiques")
    void aplatitLesAxesDeStatistiques() {
        projector.project(new CachedMatch("EUW1_7987650481",
                Fixtures.document("match-ranked-solo.json"), MAINTENANT));

        MatchParticipation ligne = capture().get(0);
        assertThat(ligne.queue()).isEqualTo(QueueKind.RANKED_SOLO);
        assertThat(ligne.side()).isIn(100, 200);
        assertThat(ligne.championName()).isNotBlank();
        assertThat(ligne.patch()).isEqualTo("16.18");
        assertThat(ligne.id()).isEqualTo(MatchParticipation.idOf(ligne.puuid(), "EUW1_7987650481"));
    }

    @Test
    @DisplayName("une reconstruction écrase sur place : les statistiques restent lisibles pendant qu'elle tourne")
    void reconstruitSansPurger() {
        when(matches.findByMatchIdGreaterThanOrderByMatchIdAsc(anyString(), any(Limit.class))).thenReturn(List.of());

        projector.rebuildAll();

        verify(participations, never()).deleteAll();
    }

    @Test
    @DisplayName("une partie stockée sans JSON brut est comptée, pas ignorée en silence")
    void compteLesPartiesSansBrut() {
        when(matches.findByMatchIdGreaterThanOrderByMatchIdAsc(anyString(), any(Limit.class))).thenReturn((List.of(
                new CachedMatch("EUW1_7987650481", Fixtures.document("match-ranked-solo.json"), MAINTENANT),
                new CachedMatch("EUW1_ANCIENNE", null, MAINTENANT))));

        RebuildReport rapport = projector.rebuildAll();

        assertThat(rapport.matchesRead()).isEqualTo(2);
        assertThat(rapport.participationsWritten()).isEqualTo(10);
        assertThat(rapport.unusableMatches()).isEqualTo(1);
    }

    @Test
    @DisplayName("la mise à niveau ne relit que les lignes périmées, et une partie sans brut ne tourne pas en boucle")
    void metANiveauSansBoucler() {
        when(mongo.find(any(Query.class), eq(Document.class), eq(MatchParticipation.COLLECTION)))
                .thenReturn(List.of(new Document("matchId", "EUW1_7987650481"), new Document("matchId", "EUW1_PERDUE")))
                .thenReturn(List.of());
        when(matches.findByMatchIdIn(List.of("EUW1_7987650481", "EUW1_PERDUE"))).thenReturn(List.of(
                new CachedMatch("EUW1_7987650481", Fixtures.document("match-ranked-solo.json"), MAINTENANT)));

        RebuildReport rapport = projector.upgradeOutdated();

        assertThat(rapport.participationsWritten()).isEqualTo(10);
        assertThat(rapport.unusableMatches()).isEqualTo(1);
        verify(participations, never()).deleteAll();
    }

    @Test
    @DisplayName("vision, survie et objectifs sont projetés bruts, avec les dégâts de toute l'équipe pour la part")
    void projetteLaPerformance() {
        projector.project(new CachedMatch(MATCH, partieEnrichie(), MAINTENANT));

        MatchParticipation top = ligne(TOP_BLEU);
        assertThat(top.performance()).isEqualTo(new MatchParticipation.Performance(12, 4, 3, 95, 5200, 2, 1800,
                15272 + 22035 + 20062 + 15985 + 12754, 5));
    }

    @Test
    @DisplayName("l'adversaire direct est l'autre joueur du même poste, et l'écart de plaques se lit sans timeline")
    void trouveLAdversaireDirect() {
        projector.project(new CachedMatch(MATCH, partieEnrichie(), MAINTENANT));

        MatchParticipation.Laning laning = ligne(TOP_BLEU).laning();
        assertThat(laning.opponentPuuid()).isEqualTo(TOP_ROUGE);
        assertThat(laning.platesDiff()).isEqualTo(3);
        assertThat(laning.goldDiffAt15()).isNull();
    }

    @Test
    @DisplayName("avec les chiffres à 15 min des deux, les écarts d'or, de CS, d'xp et de kills sont projetés")
    void projetteLesEcartsA15() {
        when(earlyStats.findById(MATCH)).thenReturn(Optional.of(new MatchEarlyStats(MATCH, Map.of(
                TOP_BLEU, new MatchInsights.At15(6000, 7000, 130, 4000, 2, 1, 0),
                TOP_ROUGE, new MatchInsights.At15(5200, 6500, 118, 3000, 1, 2, 1)), null, MatchEarlyStats.CURRENT_VERSION)));

        projector.project(new CachedMatch(MATCH, partieEnrichie(), MAINTENANT));

        MatchParticipation.Laning laning = ligne(TOP_BLEU).laning();
        assertThat(laning.goldAt15()).isEqualTo(6000);
        assertThat(laning.goldDiffAt15()).isEqualTo(800);
        assertThat(laning.csDiffAt15()).isEqualTo(12);
        assertThat(laning.xpDiffAt15()).isEqualTo(500);
        assertThat(laning.killsDiffAt15()).isEqualTo(1);
    }

    @Test
    @DisplayName("deux joueurs au même poste dans une équipe : pas d'adversaire plutôt qu'un faux")
    void pasDAdversaireSurPosteEnDouble() {
        MatchParticipant a = joueur("a", TeamPosition.TOP, 100);
        MatchParticipant b = joueur("b", TeamPosition.TOP, 100);
        MatchParticipant c = joueur("c", TeamPosition.TOP, 200);
        MatchParticipant d = joueur("d", TeamPosition.UNKNOWN, 100);
        MatchParticipant e = joueur("e", TeamPosition.UNKNOWN, 200);

        assertThat(ParticipationProjector.adversaires(List.of(a, b, c, d, e))).isEmpty();
    }

    @Test
    @DisplayName("un rang relevé à la date de la partie l'emporte ; les autres prennent le rang estimé de la partie")
    void figeLeRangDeLaPartie() {
        when(rankHistory.rankAt(any(), any(), any())).thenReturn(Map.of(TOP_BLEU,
                new RankSpan("s", TOP_BLEU, QueueKind.RANKED_SOLO, "PLATINUM", "II", 10, MAINTENANT, MAINTENANT)));
        when(lobbies.findById(MATCH)).thenReturn(Optional.of(new MatchLobby(MATCH, "graine", "GOLD", "I", MAINTENANT)));

        projector.project(new CachedMatch(MATCH, partieEnrichie(), MAINTENANT));

        assertThat(ligne(TOP_BLEU).rank()).isEqualTo(new MatchParticipation.RankAtGame("PLATINUM", "II", false));
        assertThat(ligne(TOP_ROUGE).rank()).isEqualTo(new MatchParticipation.RankAtGame("GOLD", "I", true));
    }

    private static final String MATCH = "EUW1_7987650481";
    private static final String TOP_BLEU = "WXlEt6t1";
    private static final String TOP_ROUGE = "yVI0urZP";

    private static Document partieEnrichie() {
        Document brut = Fixtures.document("match-ranked-solo.json");
        List<Document> joueurs = brut.get("info", Document.class).getList("participants", Document.class);
        for (Document joueur : joueurs) {
            if (joueur.getString("puuid").startsWith(TOP_BLEU)) {
                joueur.append("wardsPlaced", 12).append("wardsKilled", 4).append("detectorWardsPlaced", 3)
                        .append("totalTimeSpentDead", 95).append("damageDealtToTurrets", 5200)
                        .append("turretTakedowns", 2).append("damageDealtToEpicMonsters", 1800)
                        .append("challenges", new Document("turretPlatesTaken", 5));
                joueur.put("puuid", TOP_BLEU);
            } else if (joueur.getString("puuid").startsWith(TOP_ROUGE)) {
                joueur.append("challenges", new Document("turretPlatesTaken", 2));
                joueur.put("puuid", TOP_ROUGE);
            }
        }
        return brut;
    }

    private MatchParticipation ligne(String puuid) {
        return capture().stream().filter(row -> row.puuid().equals(puuid)).findFirst().orElseThrow();
    }

    private static MatchParticipant joueur(String puuid, TeamPosition poste, int equipe) {
        return new MatchParticipant(puuid, null, null, 1, "X", poste, equipe, true, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0,
                List.of(), false);
    }

    @SuppressWarnings("unchecked")
    private List<MatchParticipation> capture() {
        ArgumentCaptor<List<MatchParticipation>> ecrites = ArgumentCaptor.forClass(List.class);
        verify(participations).saveAll(ecrites.capture());
        return ecrites.getValue();
    }
}
