package schultz.thomas.schub.connector.riot.business.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
