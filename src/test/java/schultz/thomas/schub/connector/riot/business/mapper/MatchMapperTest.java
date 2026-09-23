package schultz.thomas.schub.connector.riot.business.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotMatchResponse;
import schultz.thomas.schub.connector.riot.support.Fixtures;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchMapperTest {

    private final MatchMapper mapper = new MatchMapper();

    @Test
    @DisplayName("une partie réelle de l'API Riot est traduite sans perte de ce qui compte")
    void traduitUnePartieReelle() {
        RiotMatchResponse response = Fixtures.load("match-ranked-solo.json", RiotMatchResponse.class);

        MatchDetail match = mapper.toDomain(response);

        assertThat(match.matchId()).isEqualTo("EUW1_7987650481");
        assertThat(match.queueId()).isEqualTo(420);
        assertThat(match.queue()).isEqualTo(QueueKind.RANKED_SOLO);
        assertThat(match.platform()).isEqualTo("EUW1");
        assertThat(match.gameVersion()).isEqualTo("16.18.817.5716");
        assertThat(match.complete()).isTrue();
        assertThat(match.participants()).hasSize(10);
        assertThat(match.teams()).hasSize(2);
    }

    @Test
    @DisplayName("les dix participants sont rendus : le connecteur ne juge pas qui compte")
    void rendLesDixParticipants() {
        MatchDetail match = mapper.toDomain(
                Fixtures.load("match-ranked-solo.json", RiotMatchResponse.class));

        assertThat(match.participants()).extracting(MatchParticipant::puuid).doesNotContainNull();
        assertThat(match.participants()).extracting(MatchParticipant::teamId)
                .containsOnly(100, 200);
        assertThat(match.participants()).extracting(MatchParticipant::position)
                .doesNotContain(TeamPosition.UNKNOWN);

        MatchParticipant premier = match.participants().get(0);
        assertThat(premier.championName()).isEqualTo("Gragas");
        assertThat(premier.position()).isEqualTo(TeamPosition.TOP);
        assertThat(premier.minionsKilled()).isEqualTo(162);
        assertThat(premier.items()).hasSize(7);
    }

    @Test
    @DisplayName("les objectifs sont rendus tels quels, y compris ceux ajoutés après coup")
    void rendLesObjectifsSansLesEnumerer() {
        MatchDetail match = mapper.toDomain(
                Fixtures.load("match-ranked-solo.json", RiotMatchResponse.class));

        Map<String, Integer> objectifs = match.teams().get(0).objectives();
        assertThat(objectifs).containsKeys("baron", "dragon", "tower", "champion", "atakhan");
        assertThat(match.teams().get(0).bannedChampionIds()).hasSize(5);
    }

    @Test
    @DisplayName("avant le patch 11.20, gameDuration est en millisecondes")
    void tolereLAncienneUniteDeDuree() {
        RiotMatchResponse ancienne = partieSynthetique(0L, 1_800_000L);
        MatchDetail match = mapper.toDomain(ancienne);

        assertThat(match.durationSeconds()).isEqualTo(1800);
        assertThat(match.endedAt()).isEqualTo(match.startedAt().plusSeconds(1800));
    }

    @Test
    @DisplayName("depuis le patch 11.20, gameDuration est en secondes")
    void litLUniteCourante() {
        Instant fin = Instant.parse("2026-09-18T12:30:00Z");
        RiotMatchResponse recente = partieSynthetique(fin.toEpochMilli(), 1800L);

        MatchDetail match = mapper.toDomain(recente);

        assertThat(match.durationSeconds()).isEqualTo(1800);
        assertThat(match.endedAt()).isEqualTo(fin);
    }

    @Test
    @DisplayName("un remake est rendu, marqué incomplet — c'est au cœur de décider s'il compte")
    void marqueLesPartiesIncompletes() {
        RiotMatchResponse remake = new RiotMatchResponse(
                new RiotMatchResponse.Metadata("2", "EUW1_1", List.of()),
                new RiotMatchResponse.Info(1L, 420, "CLASSIC", "MATCHED_GAME", "16.18.1", "EUW1",
                        0L, Instant.parse("2026-09-18T12:00:00Z").toEpochMilli(),
                        Instant.parse("2026-09-18T12:03:00Z").toEpochMilli(), 180L,
                        "Abort_Unexpected", List.of(), List.of()));

        assertThat(mapper.toDomain(remake).complete()).isFalse();
    }

    private RiotMatchResponse partieSynthetique(long finEnMillis, long duree) {
        return new RiotMatchResponse(
                new RiotMatchResponse.Metadata("2", "EUW1_1", List.of()),
                new RiotMatchResponse.Info(1L, 420, "CLASSIC", "MATCHED_GAME", "11.19.1", "EUW1",
                        0L, Instant.parse("2026-09-18T12:00:00Z").toEpochMilli(),
                        finEnMillis, duree, "GameComplete", List.of(), List.of()));
    }
}
