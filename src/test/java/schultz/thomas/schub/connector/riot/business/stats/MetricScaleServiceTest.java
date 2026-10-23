package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;
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

    @Test
    @DisplayName("Les axes du radar se moyennent sur les parties où ils sont connus ; sans timeline, ni écart ni K+A à 15")
    void axesDuRadar() {
        Document base = new Document("games", 10).append("wins", 5).append("kills", 40).append("deaths", 20)
                .append("assists", 60).append("secondsPlayed", 18_000).append("damageToChampions", 200_000)
                .append("teamDamageToChampions", 800_000).append("teamKills", 200).append("teamDeaths", 200);

        MetricScaleService.Joueur avec = MetricScaleService.Joueur.of(new Document(base)
                .append("laningGames", 4).append("goldDiffAt15", 1_200).append("csDiffAt15", 20)
                .append("earlyGames", 5).append("killsAssistsAt15", 15)
                .append("soloKillsGames", 10).append("soloKills", 7));
        MetricScaleService.Joueur sans = MetricScaleService.Joueur.of(base);

        assertThat(avec.damageShare()).isCloseTo(0.25, within(1e-9));
        assertThat(avec.goldDiffAt15()).isCloseTo(300, within(1e-9));
        assertThat(avec.csDiffAt15()).isCloseTo(5, within(1e-9));
        assertThat(avec.killsAssistsAt15()).isCloseTo(3, within(1e-9));
        assertThat(avec.soloKills()).isCloseTo(0.7, within(1e-9));
        assertThat(sans.goldDiffAt15()).isNull();
        assertThat(sans.killsAssistsAt15()).isNull();
        assertThat(sans.soloKills()).isNull();
    }
}
