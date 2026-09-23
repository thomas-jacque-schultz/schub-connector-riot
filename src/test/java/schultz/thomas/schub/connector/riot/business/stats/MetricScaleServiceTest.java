package schultz.thomas.schub.connector.riot.business.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricScaleServiceTest {

    @Test
    @DisplayName("Le quantile s'interpole entre deux joueurs")
    void quantile() {
        double[] valeurs = {100, 200, 300, 400, 500};
        assertThat(MetricScaleService.quantile(valeurs, 0.0)).isEqualTo(100);
        assertThat(MetricScaleService.quantile(valeurs, 0.95)).isCloseTo(480, within(1e-9));
        assertThat(MetricScaleService.quantile(new double[] {42}, 0.05)).isEqualTo(42);
    }

    @Test
    @DisplayName("16.9 précède 16.13")
    void ordreDesPatches() {
        assertThat(List.of("16.13", "16.9", "15.24", "16.10").stream()
                .sorted(MetricScaleService.PAR_VERSION.reversed()).toList())
                .containsExactly("16.13", "16.10", "16.9", "15.24");
    }
}
