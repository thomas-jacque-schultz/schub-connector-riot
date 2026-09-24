package schultz.thomas.schub.connector.riot.business.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.data.model.StoredReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankMeansTest {

    private static final List<Double> P = List.of(0.0, 0.25, 0.5, 0.75, 1.0);
    private static final long ASSEZ = ReferenceService.MINIMUM_PARTIES;

    // Valeurs centrales et écarts interquartiles relevés en dev au poste BOTTOM, patchs 16.18 et 16.19.
    @Test
    @DisplayName("le farm monte d'un palier à l'autre, bien plus que l'écart entre deux parties : il suit le rang")
    void farmSuitLeRang() {
        Map<String, StoredReference.TierGrid> tiers = paliers(1.66,
                5.61, 5.97, 6.77, 6.96, 7.31, 7.45, 7.9, 8.1);

        assertThat(RankMeans.of(P, tiers, ASSEZ, ReferenceMetric.Polarity.HIGHER))
                .containsKeys("IRON", "MASTER_PLUS")
                .hasEntrySatisfying("GOLD", moyenne -> assertThat(moyenne).isCloseTo(6.96, within(1e-9)));
    }

    @Test
    @DisplayName("le KDA bouge de 0,3 d'Iron à Maître quand deux parties d'un même palier diffèrent de 2,3 : pas de rang")
    void kdaNeSuitPasLeRang() {
        Map<String, StoredReference.TierGrid> tiers = paliers(2.31, 2.0, 2.13, 2.09, 2.17, 2.25, 2.33, 2.2, 2.31);

        assertThat(RankMeans.of(P, tiers, ASSEZ, ReferenceMetric.Polarity.HIGHER)).isNull();
    }

    @Test
    @DisplayName("balises d'un tireur : médiane à zéro dans tous les paliers, mais la moyenne monte, et c'est elle qui compte")
    void balisesDUnTireur() {
        List<Double> p = List.of(0.0, 0.25, 0.5, 0.75, 0.9, 1.0);
        Map<String, StoredReference.TierGrid> tiers = new LinkedHashMap<>();
        tiers.put("IRON", new StoredReference.TierGrid(ASSEZ, List.of(0.0, 0.0, 0.0, 0.0, 1.0, 2.0)));
        tiers.put("GOLD", new StoredReference.TierGrid(ASSEZ, List.of(0.0, 0.0, 0.0, 1.0, 1.0, 3.0)));
        tiers.put("DIAMOND", new StoredReference.TierGrid(ASSEZ, List.of(0.0, 0.0, 0.0, 1.0, 2.0, 4.0)));

        Map<String, Double> moyennes = RankMeans.of(p, tiers, ASSEZ, ReferenceMetric.Polarity.HIGHER);

        assertThat(moyennes).isNotNull();
        assertThat(moyennes.get("IRON")).isLessThan(moyennes.get("GOLD"));
        assertThat(moyennes.get("GOLD")).isLessThan(moyennes.get("DIAMOND"));
    }

    @Test
    @DisplayName("la moyenne est l'aire sous la fonction quantile")
    void moyenne() {
        assertThat(RankMeans.moyenne(List.of(0.0, 0.5, 1.0), List.of(0.0, 1.0, 5.0))).isCloseTo(1.75, within(1e-9));
    }

    @Test
    @DisplayName("moins vaut mieux : les moyennes doivent descendre avec le rang")
    void polariteInversee() {
        Map<String, StoredReference.TierGrid> tiers = paliers(0.1, 0.5, 0.4, 0.3, 0.2, 0.1, 0.05, 0.0, -0.1);

        assertThat(RankMeans.of(P, tiers, ASSEZ, ReferenceMetric.Polarity.LOWER)).hasSize(8);
        assertThat(RankMeans.of(P, tiers, ASSEZ, ReferenceMetric.Polarity.HIGHER)).isNull();
    }

    @Test
    @DisplayName("un palier sous l'effectif minimum ne compte pas, et deux paliers ne suffisent pas")
    void effectifs() {
        Map<String, StoredReference.TierGrid> tiers = new LinkedHashMap<>();
        tiers.put("IRON", grille(ASSEZ, 1, 5));
        tiers.put("GOLD", grille(ASSEZ - 1, 1, 6));
        tiers.put("DIAMOND", grille(ASSEZ, 1, 8));

        assertThat(RankMeans.of(P, tiers, ASSEZ, ReferenceMetric.Polarity.HIGHER)).isNull();

        tiers.put("GOLD", grille(ASSEZ, 1, 6));
        assertThat(RankMeans.of(P, tiers, ASSEZ, ReferenceMetric.Polarity.HIGHER))
                .containsExactly(Map.entry("IRON", 5.0), Map.entry("GOLD", 6.0), Map.entry("DIAMOND", 8.0));
    }

    @Test
    @DisplayName("une métrique neutre n'a pas de rang")
    void neutre() {
        assertThat(RankMeans.of(P, paliers(1, 1, 2, 3, 4, 5, 6, 7, 8), ASSEZ, ReferenceMetric.Polarity.NEUTRAL))
                .isNull();
    }

    @Test
    @DisplayName("corrélation de rang : 1 si tout monte, les ex æquo prennent leur rang moyen")
    void spearman() {
        assertThat(RankMeans.spearman(List.of(1.0, 2.0, 3.0))).isCloseTo(1, within(1e-9));
        assertThat(RankMeans.spearman(List.of(3.0, 2.0, 1.0))).isCloseTo(-1, within(1e-9));
        assertThat(RankMeans.spearman(List.of(1.0, 1.0, 2.0))).isCloseTo(0.866, within(1e-3));
        assertThat(RankMeans.spearman(List.of(2.0, 2.0, 2.0))).isZero();
    }

    private static Map<String, StoredReference.TierGrid> paliers(double interquartile, double... medianes) {
        Map<String, StoredReference.TierGrid> tiers = new LinkedHashMap<>();
        for (int i = 0; i < medianes.length; i++) {
            tiers.put(ReferenceService.PALIERS.get(i), grille(ASSEZ, interquartile, medianes[i]));
        }
        return tiers;
    }

    private static StoredReference.TierGrid grille(long effectif, double interquartile, double mediane) {
        return new StoredReference.TierGrid(effectif, List.of(mediane - interquartile, mediane - interquartile / 2,
                mediane, mediane + interquartile / 2, mediane + interquartile));
    }
}
