package schultz.thomas.schub.connector.riot.business.search;

import java.text.Normalizer;
import java.util.Locale;

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
