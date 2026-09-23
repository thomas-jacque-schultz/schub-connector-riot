package schultz.thomas.schub.connector.riot.business.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchEarlyStatsRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchRankSnapshotRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchInsightsFromSeatsTest {

    @Mock private CachedMatchRepository matches;
    @Mock private MatchParticipationRepository participations;
    @Mock private MatchEarlyStatsRepository earlyStats;
    @Mock private MatchRankSnapshotRepository rankSnapshots;
    @InjectMocks private MatchEnrichmentService service;

    @Test
    @DisplayName("les places viennent des participations, rangées par camp puis par poste, sans relire le brut")
    void placesDesParticipations() {
        when(earlyStats.findByMatchIdIn(anyCollection())).thenReturn(List.of());
        when(rankSnapshots.findByMatchIdIn(anyCollection())).thenReturn(List.of());
        when(participations.findSeatsByMatchIdIn(anyCollection())).thenReturn(List.of(
                place("b", 200, TeamPosition.TOP), place("a2", 100, TeamPosition.MIDDLE),
                place("a1", 100, TeamPosition.TOP)));

        List<MatchInsights> insights = service.insights(List.of("EUW1_1"));

        assertThat(insights).singleElement().satisfies(partie -> {
            assertThat(partie.timelineAvailable()).isFalse();
            assertThat(partie.participants()).extracting(MatchInsights.Participant::puuid)
                    .containsExactly("a1", "a2", "b");
        });
        verifyNoInteractions(matches);
    }

    private static MatchParticipation place(String puuid, int side, TeamPosition position) {
        return new MatchParticipation(null, puuid, "EUW1_1", null, null, null, 1, null, position, false, side, 0, 0,
                null, null, null, null, null, false, 0, 0, 0, 0, 0, 0, 0, 0, null, null, false, null, null, null,
                null, null);
    }
}
