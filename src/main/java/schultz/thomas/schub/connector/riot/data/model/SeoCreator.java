package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Par Riot ID et non par puuid : un puuid change avec la clé d'API, et ce réglage survit à la purge.
@Document("riot_seo_creator")
public record SeoCreator(
        @Id String key,
        String gameName,
        String tagLine,
        Instant addedAt
) {

    public static SeoCreator of(String gameName, String tagLine, Instant addedAt) {
        return new SeoCreator((gameName + "#" + tagLine).toLowerCase(java.util.Locale.ROOT), gameName, tagLine, addedAt);
    }

    public String riotId() {
        return gameName + "#" + tagLine;
    }
}
