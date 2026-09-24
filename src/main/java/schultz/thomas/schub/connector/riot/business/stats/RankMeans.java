package schultz.thomas.schub.connector.riot.business.stats;

import schultz.thomas.schub.connector.riot.data.model.StoredReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Une valeur prend le palier dont la moyenne est la plus proche. Ça n'a de sens que si les moyennes montent d'un
// palier à l'autre, et d'assez haut face à l'écart ordinaire entre deux parties d'un même palier : le KDA, les
// écarts à 15 min, les objectifs ne le font pas — chacun joue contre son propre rang, et avec son équipe.
final class RankMeans {

    static final int PALIERS_MINIMUM = 3;
    static final double CORRELATION_MINIMUM = 0.8;
    // Écart des moyennes du plus bas au plus haut palier, rapporté à l'écart interquartile moyen d'un palier.
    static final double ECART_MINIMUM = 0.4;

    private RankMeans() {
    }

    static Map<String, Double> of(List<Double> percentiles, Map<String, StoredReference.TierGrid> tiers, long minimum,
                                  ReferenceMetric.Polarity polarity) {
        int bas = percentiles.indexOf(0.25);
        int haut = percentiles.indexOf(0.75);
        if (polarity == ReferenceMetric.Polarity.NEUTRAL || bas < 0 || haut < 0) {
            return null;
        }
        Map<String, Double> moyennes = new LinkedHashMap<>();
        double interquartiles = 0;
        for (String palier : ReferenceService.PALIERS) {
            StoredReference.TierGrid grille = tiers.get(palier);
            if (grille != null && grille.count() >= minimum) {
                moyennes.put(palier, moyenne(percentiles, grille.values()));
                interquartiles += grille.values().get(haut) - grille.values().get(bas);
            }
        }
        if (moyennes.size() < PALIERS_MINIMUM || interquartiles <= 0) {
            return null;
        }
        double sens = polarity == ReferenceMetric.Polarity.LOWER ? -1 : 1;
        List<Double> orientees = moyennes.values().stream().map(moyenne -> sens * moyenne).toList();
        double ecart = (orientees.getLast() - orientees.getFirst()) / (interquartiles / moyennes.size());
        return ecart >= ECART_MINIMUM && spearman(orientees) >= CORRELATION_MINIMUM ? moyennes : null;
    }

    // La moyenne est l'aire sous la fonction quantile : une médiane de 0 balise cache une moyenne qui monte.
    static double moyenne(List<Double> percentiles, List<Double> valeurs) {
        double aire = 0;
        for (int i = 1; i < percentiles.size(); i++) {
            aire += (percentiles.get(i) - percentiles.get(i - 1)) * (valeurs.get(i) + valeurs.get(i - 1)) / 2;
        }
        return aire;
    }

    // Corrélation de rang entre l'ordre des paliers et celui des moyennes ; les ex æquo prennent leur rang moyen.
    static double spearman(List<Double> valeurs) {
        int n = valeurs.size();
        double[] rangs = new double[n];
        for (int i = 0; i < n; i++) {
            int dessous = 0;
            int egaux = 0;
            for (double autre : valeurs) {
                int comparaison = Double.compare(autre, valeurs.get(i));
                if (comparaison < 0) {
                    dessous++;
                } else if (comparaison == 0) {
                    egaux++;
                }
            }
            rangs[i] = dessous + (egaux - 1) / 2.0;
        }
        double centre = (n - 1) / 2.0;
        double covariance = 0;
        double varianceOrdre = 0;
        double varianceRangs = 0;
        for (int i = 0; i < n; i++) {
            covariance += (i - centre) * (rangs[i] - centre);
            varianceOrdre += (i - centre) * (i - centre);
            varianceRangs += (rangs[i] - centre) * (rangs[i] - centre);
        }
        return varianceRangs <= 0 ? 0 : covariance / Math.sqrt(varianceOrdre * varianceRangs);
    }
}
