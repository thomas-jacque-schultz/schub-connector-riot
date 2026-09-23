package schultz.thomas.schub.connector.riot.business.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LadderMixtureTest {

    private static final List<Double> P = List.of(0.0, 0.25, 0.5, 0.75, 1.0);

    @Test
    @DisplayName("deux paliers identiques : le mélange est leur grille")
    void paliersIdentiques() {
        List<Double> grille = List.of(1.0, 2.0, 3.0, 4.0, 5.0);

        assertThat(LadderMixture.mix(P, Map.of("GOLD", grille, "SILVER", grille), Map.of("GOLD", 10.0, "SILVER", 30.0)))
                .usingComparatorForType((a, b) -> Math.abs(a - b) < 1e-6 ? 0 : Double.compare(a, b), Double.class)
                .containsExactlyElementsOf(grille);
    }

    @Test
    @DisplayName("un palier pèse sa part du ladder, pas son effectif : la médiane glisse vers le palier le plus lourd")
    void ponderationParLaRepartition() {
        Map<String, List<Double>> grilles = Map.of(
                "IRON", List.of(0.0, 0.25, 0.5, 0.75, 1.0),
                "GOLD", List.of(1.0, 1.25, 1.5, 1.75, 2.0));

        List<Double> melange = LadderMixture.mix(P, grilles, Map.of("IRON", 1.0, "GOLD", 3.0));

        assertThat(melange.get(1)).isCloseTo(1.0, within(1e-6));
        assertThat(melange.get(2)).isCloseTo(1.0 + 1.0 / 3, within(1e-6));
    }

    @Test
    @DisplayName("la répartition s'interpole entre deux quantiles, et plafonne hors de la grille")
    void cdf() {
        List<Double> grille = List.of(10.0, 20.0, 30.0, 40.0, 50.0);

        assertThat(LadderMixture.cdf(P, grille, 25)).isCloseTo(0.375, within(1e-9));
        assertThat(LadderMixture.cdf(P, grille, 5)).isZero();
        assertThat(LadderMixture.cdf(P, grille, 60)).isEqualTo(1);
    }

    @Test
    @DisplayName("une métrique en marches (tours détruites) ne divise pas par zéro")
    void grilleEnMarches() {
        List<Double> grille = List.of(0.0, 0.0, 1.0, 1.0, 3.0);

        assertThat(LadderMixture.cdf(P, grille, 0)).isCloseTo(0.25, within(1e-9));
        assertThat(LadderMixture.cdf(P, grille, 1)).isCloseTo(0.75, within(1e-9));
    }
}
