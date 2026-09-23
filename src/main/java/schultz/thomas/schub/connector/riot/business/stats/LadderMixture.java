package schultz.thomas.schub.connector.riot.business.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// La population collectée n'a pas la répartition du ladder : chaque palier y pèse sa part réelle, pas son effectif.
final class LadderMixture {

    private static final int ITERATIONS = 60;

    private LadderMixture() {
    }

    static List<Double> mix(List<Double> percentiles, Map<String, List<Double>> grilles, Map<String, Double> poids) {
        double total = grilles.keySet().stream().mapToDouble(palier -> poids.getOrDefault(palier, 0.0)).sum();
        double bas = grilles.values().stream().mapToDouble(List::getFirst).min().orElse(0);
        double haut = grilles.values().stream().mapToDouble(List::getLast).max().orElse(0);
        List<Double> melange = new ArrayList<>();
        for (double p : percentiles) {
            if (p <= 0) {
                melange.add(bas);
                continue;
            }
            if (p >= 1) {
                melange.add(haut);
                continue;
            }
            double a = bas;
            double b = haut;
            for (int i = 0; i < ITERATIONS; i++) {
                double milieu = (a + b) / 2;
                double f = 0;
                for (Map.Entry<String, List<Double>> grille : grilles.entrySet()) {
                    f += poids.getOrDefault(grille.getKey(), 0.0) * cdf(percentiles, grille.getValue(), milieu);
                }
                if (f / total < p) {
                    a = milieu;
                } else {
                    b = milieu;
                }
            }
            melange.add((a + b) / 2);
        }
        return melange;
    }

    static double cdf(List<Double> percentiles, List<Double> valeurs, double x) {
        if (x < valeurs.getFirst()) {
            return 0;
        }
        if (x >= valeurs.getLast()) {
            return 1;
        }
        int i = 0;
        while (valeurs.get(i + 1) <= x) {
            i++;
        }
        double v0 = valeurs.get(i);
        double v1 = valeurs.get(i + 1);
        return percentiles.get(i) + (percentiles.get(i + 1) - percentiles.get(i)) * (x - v0) / (v1 - v0);
    }
}
