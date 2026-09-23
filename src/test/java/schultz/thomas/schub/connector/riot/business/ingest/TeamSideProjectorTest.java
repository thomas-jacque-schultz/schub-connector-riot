package schultz.thomas.schub.connector.riot.business.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.api.dto.EarlyGame;
import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.TeamSide;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TeamSideProjectorTest {

    private static final Instant QUAND = Instant.parse("2026-09-20T18:00:00Z");

    @Test
    @DisplayName("un camp : écarts à l'autre camp à 15 min, ses objectifs, ses ganks décisifs faits et subis")
    void projetteUnCamp() {
        List<MatchParticipation> lignes = IntStream.range(0, 10)
                .mapToObj(i -> ligne("p" + i, i < 5 ? 100 : 200, i < 5 ? "GOLD" : "SILVER"))
                .toList();
        Map<String, MatchInsights.At15> a15 = new HashMap<>();
        IntStream.range(0, 10).forEach(i -> a15.put("p" + i,
                new MatchInsights.At15(i < 5 ? 5000 : 4800, 6000, 100, 2000, i == 0 ? 2 : 0, 0, 0)));
        EarlyGame early = new EarlyGame(List.of(
                gank(100, true), gank(100, false), gank(200, true)), List.of(),
                List.of(new EarlyGame.Objectives(100, 1, 3, 0), new EarlyGame.Objectives(200, 0, 3, 1)));

        List<TeamSide> camps = TeamSideProjector.camps(new MatchEarlyStats("EUW1_1", a15, early, 5), lignes);

        TeamSide bleu = camps.stream().filter(camp -> camp.side() == 100).findFirst().orElseThrow();
        assertThat(bleu.goldDiffAt15()).isEqualTo(1000);
        assertThat(bleu.killsDiffAt15()).isEqualTo(2);
        assertThat(bleu.dragons()).isEqualTo(1);
        assertThat(bleu.ganksDecisive()).isEqualTo(1);
        assertThat(bleu.ganksConceded()).isEqualTo(1);
        assertThat(bleu.tier()).isEqualTo("GOLD");
        assertThat(camps).hasSize(2);
    }

    @Test
    @DisplayName("un joueur sans chiffres à 15 min : pas d'écart plutôt qu'un écart sur quatre")
    void pasDEcartIncomplet() {
        List<MatchParticipation> lignes = IntStream.range(0, 10)
                .mapToObj(i -> ligne("p" + i, i < 5 ? 100 : 200, null))
                .toList();
        Map<String, MatchInsights.At15> a15 = new HashMap<>();
        IntStream.range(1, 10).forEach(i -> a15.put("p" + i, new MatchInsights.At15(5000, 6000, 100, 2000, 0, 0, 0)));

        TeamSide bleu = TeamSideProjector.camps(new MatchEarlyStats("EUW1_1", a15, null, 5), lignes).getFirst();

        assertThat(bleu.goldDiffAt15()).isNull();
        assertThat(bleu.tier()).isNull();
    }

    @Test
    @DisplayName("le palier d'un camp est la moyenne des paliers connus, arrondie")
    void palierMoyen() {
        assertThat(TeamSideProjector.palier(List.of(ligne("a", 100, "GOLD"), ligne("b", 100, "PLATINUM"),
                ligne("c", 100, "PLATINUM"), ligne("d", 100, null)))).isEqualTo("PLATINUM");
        assertThat(TeamSideProjector.palier(List.of(ligne("a", 100, "CHALLENGER")))).isEqualTo("MASTER_PLUS");
    }

    private static EarlyGame.Gank gank(int camp, boolean decisif) {
        return new EarlyGame.Gank(300, EarlyGame.Lane.TOP, camp, "j", List.of(), EarlyGame.Outcome.KILL,
                decisif ? 1 : 0, decisif ? 0 : 1, List.of(), false);
    }

    private static MatchParticipation ligne(String puuid, int side, String tier) {
        return new MatchParticipation(MatchParticipation.idOf(puuid, "EUW1_1"), puuid, "EUW1_1", "P", "T", "p", 1,
                "X", TeamPosition.TOP, side == 100, side, 1800, 420, QueueKind.RANKED_SOLO, "16.18.1", "16.18",
                "EUW1", QUAND, true, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, null, null,
                tier == null ? null : new MatchParticipation.RankAtGame(tier, "I", false),
                MatchParticipation.PROJECTION_VERSION, QUAND);
    }
}
