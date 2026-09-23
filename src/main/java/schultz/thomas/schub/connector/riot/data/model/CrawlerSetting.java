package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("riot_crawler_setting")
public record CrawlerSetting(@Id String id, boolean enabled, Instant updatedAt) {

    public static final String CURRENT = "current";
}
