package schultz.thomas.schub.connector.riot.business.stats;

import schultz.thomas.schub.connector.riot.data.model.StoredReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Une valeur prend le palier dont la médiane est la plus proche. Ça n'a de sens que si les médianes montent d'un
// palier à l'autre, et d'assez haut pour dépasser l'écart ordinaire entre deux parties d'un même palier : le KDA,
// les écarts à 15 min ne le font pas, chacun jouant contre son propre rang.
final class RankMedians {

    static final int PALIERS_MINIMUM = 3;
    static final double CORRELATION_MINIMUM = 0.8;
    // Écart des médianes du plus bas au plus haut palier, rapporté à l'écart interquartile moyen d'un palier.
    static final double ECART_MINIMUM = 0.5;

    private RankMedians() {
    }

    static Map<String, Double> of(List<Double> percentiles, Map<String, StoredReference.TierGrid> tiers, long minimum,
                                  ReferenceMetric.Polarity polarity) {
        int bas = percentiles.indexOf(0.25);
        int milieu = percentiles.indexOf(0.5);
        int haut = percentiles.indexOf(0.75);
        if (polarity == ReferenceMetric.Polarity.NEUTRAL || bas < 0 || milieu < 0 || haut < 0) {
            return null;
        }
        Map<String, Double> medianes = new LinkedHashMap<>();
        double interquartiles = 0;
        for (String palier : ReferenceService.PALIERS) {
            StoredReference.TierGrid grille = tiers.get(palier);
            if (grille != null && grille.count() >= minimum) {
                medianes.put(palier, grille.values().get(milieu));
                interquartiles += grille.values().get(haut) - grille.values().get(bas);
            }
        }
        if (medianes.size() < PALIERS_MINIMUM || interquartiles <= 0) {
            return null;
        }
        double sens = polarity == ReferenceMetric.Polarity.LOWER ? -1 : 1;
        List<Double> orientees = medianes.values().stream().map(mediane -> sens * mediane).toList();
        double ecart = (orientees.getLast() - orientees.getFirst()) / (interquartiles / medianes.size());
        return ecart >= ECART_MINIMUM && spearman(orientees) >= CORRELATION_MINIMUM ? medianes : null;
    }

    // Corrélation de rang entre l'ordre des paliers et celui des médianes ; les ex æquo prennent leur rang moyen.
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
        double moyenne = (n - 1) / 2.0;
        double covariance = 0;
        double varianceOrdre = 0;
        double varianceRangs = 0;
        for (int i = 0; i < n; i++) {
            covariance += (i - moyenne) * (rangs[i] - moyenne);
            varianceOrdre += (i - moyenne) * (i - moyenne);
            varianceRangs += (rangs[i] - moyenne) * (rangs[i] - moyenne);
        }
        return varianceRangs <= 0 ? 0 : covariance / Math.sqrt(varianceOrdre * varianceRangs);
    }
}
