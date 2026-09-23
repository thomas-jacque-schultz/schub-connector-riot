package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCatalog;

import java.time.Instant;

@Document("riot_champion_catalog")
public record CachedChampionCatalog(@Id String id, ChampionCatalog catalog, Instant fetchedAt) {

    public static String idOf(String version, String locale) {
        return version + "#" + locale;
    }
}
