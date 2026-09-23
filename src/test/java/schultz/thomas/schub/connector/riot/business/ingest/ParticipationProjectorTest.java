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
import schultz.thomas.schub.connector.riot.api.dto.RebuildReport;
import schultz.thomas.schub.connector.riot.business.mapper.MatchMapper;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.support.Fixtures;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.List;

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

    private ParticipationProjector projector;

    @BeforeEach
    void setUp() {
        projector = new ParticipationProjector(matches, participations, knownAccounts,
                new RawMatchDecoder(new MatchMapper()), new TestClock(MAINTENANT), metricScale, mongo);
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

    @SuppressWarnings("unchecked")
    private List<MatchParticipation> capture() {
        ArgumentCaptor<List<MatchParticipation>> ecrites = ArgumentCaptor.forClass(List.class);
        verify(participations).saveAll(ecrites.capture());
        return ecrites.getValue();
    }
}
