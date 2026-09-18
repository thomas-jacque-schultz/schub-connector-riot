package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCatalog;

import java.time.Instant;

/**
 * Politique n°5 — <strong>permanent, la clé est la version</strong>.
 *
 * <p>Le catalogue d'une version donnée ne change plus : Riot publie une nouvelle version, il ne
 * réécrit pas l'ancienne. Le garder indéfiniment est ce qui permet de rouvrir une composition
 * préparée en mars et de la voir juste.</p>
 *
 * <p>La clé porte aussi la langue : {@code 16.18.1#fr_FR} et {@code 16.18.1#en_US} sont deux
 * catalogues, tous deux immuables.</p>
 */
@Document("riot_champion_catalog")
public record CachedChampionCatalog(@Id String id, ChampionCatalog catalog, Instant fetchedAt) {

    public static String idOf(String version, String locale) {
        return version + "#" + locale;
    }
}
