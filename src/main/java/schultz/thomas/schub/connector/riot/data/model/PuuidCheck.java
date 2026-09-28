package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("riot_puuid_check")
@CompoundIndex(name = "valid_checkedAt", def = "{'valid': 1, 'checkedAt': -1}")
public record PuuidCheck(@Id String puuid, boolean valid, Instant checkedAt) {
}
