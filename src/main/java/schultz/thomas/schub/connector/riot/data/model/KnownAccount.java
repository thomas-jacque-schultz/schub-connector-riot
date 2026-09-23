package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;

import java.time.Instant;

@Document("riot_known_account")
@CompoundIndex(name = "searchName_observedAt", def = "{'searchName': 1, 'observedAt': -1}")
public record KnownAccount(
        @Id String puuid,
        String gameName,
        String tagLine,
        @Indexed String searchName,
        Instant observedAt,
        KnownAccountSource source
) {

    public String riotId() {
        if (gameName == null || gameName.isBlank()) {
            return null;
        }
        return tagLine == null || tagLine.isBlank() ? gameName : gameName + "#" + tagLine;
    }
}
