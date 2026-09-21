package schultz.thomas.schub.connector.riot.business.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RebuildReport;
import schultz.thomas.schub.connector.riot.business.mapper.MatchMapper;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.support.Fixtures;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La couche d'analyse se construit depuis le stocké, et se reconstruit entièrement sans appel.
 */
@ExtendWith(MockitoExtension.class)
class ParticipationProjectorTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-21T10:00:00Z");

    @Mock private CachedMatchRepository matches;
    @Mock private MatchParticipationRepository participations;

    private ParticipationProjector projector;

    @BeforeEach
    void setUp() {
        projector = new ParticipationProjector(matches, participations,
                new RawMatchDecoder(new MatchMapper()), new TestClock(MAINTENANT));
    }

    @Test
    @DisplayName("une partie donne une ligne par participant, pas seulement pour le demandeur")
    void projetteLesDixParticipants() {
        projector.project(new CachedMatch("EUW1_7987650481",
                Fixtures.document("match-ranked-solo.json"), MAINTENANT));

        assertThat(capture()).hasSize(10);
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
        // Grouper sur `16.18.817.5716` ne regrouperait rien : la version change à chaque build.
        assertThat(ligne.patch()).isEqualTo("16.18");
        assertThat(ligne.id()).isEqualTo(MatchParticipation.idOf(ligne.puuid(), "EUW1_7987650481"));
    }

    @Test
    @DisplayName("une reconstruction purge d'abord, sinon un champ retiré laisserait des restes")
    void purgeAvantDeReconstruire() {
        when(matches.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        projector.rebuildAll();

        verify(participations).deleteAll();
    }

    @Test
    @DisplayName("une partie stockée sans JSON brut est comptée, pas ignorée en silence")
    void compteLesPartiesSansBrut() {
        when(matches.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                new CachedMatch("EUW1_7987650481", Fixtures.document("match-ranked-solo.json"), MAINTENANT),
                new CachedMatch("EUW1_ANCIENNE", null, MAINTENANT))));

        RebuildReport rapport = projector.rebuildAll();

        assertThat(rapport.matchesRead()).isEqualTo(2);
        assertThat(rapport.participationsWritten()).isEqualTo(10);
        // Sans ce compteur, une base d'avant le passage au brut donnerait une couche d'analyse
        // silencieusement incomplète.
        assertThat(rapport.unusableMatches()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private List<MatchParticipation> capture() {
        ArgumentCaptor<List<MatchParticipation>> ecrites = ArgumentCaptor.forClass(List.class);
        verify(participations).saveAll(ecrites.capture());
        return ecrites.getValue();
    }
}
