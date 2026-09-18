package schultz.thomas.schub.connector.riot.data.model.riot;

import java.util.List;
import java.util.Map;

/**
 * Forme brute de {@code champion.json} de Data Dragon.
 *
 * <p>Pas de clé d'API, pas de quota : c'est un CDN statique. Lui envoyer la clé la ferait
 * fuiter sans contrepartie.</p>
 *
 * @param data clé textuelle du champion ({@code MonkeyKing}) → sa fiche. Le nom affiché
 *             ({@code Wukong}) n'est pas cette clé.
 */
public record DataDragonChampionList(String type, String format, String version,
                                     Map<String, Champion> data) {

    /** @param key identifiant numérique, rendu en chaîne par Data Dragon. */
    public record Champion(String id, String key, String name, String title, List<String> tags,
                           Image image) {
    }

    public record Image(String full, String group) {
    }
}
