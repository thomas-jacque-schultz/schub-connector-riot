package schultz.thomas.schub.connector.riot.business.search;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Le repli d'un pseudo : minuscules, sans diacritiques, sans espaces ni ponctuation.
 *
 * <p>Il est stocké sur la participation et appliqué à la saisie : les deux côtés de la
 * comparaison passent par la même fonction, sinon « Rémi » ne trouverait jamais « remi ».</p>
 */
public final class SearchName {

    private SearchName() {
    }

    public static String fold(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFD);
        StringBuilder folded = new StringBuilder(decomposed.length());
        decomposed.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .forEach(folded::appendCodePoint);
        return folded.isEmpty() ? null : folded.toString().toLowerCase(Locale.ROOT);
    }
}
