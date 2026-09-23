package schultz.thomas.schub.connector.riot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "riot")
public class RiotProperties {

    private String apiKey = "";

    private String region = "europe";

    private String platform = "euw1";

    private String dataDragonBaseUrl = "https://ddragon.leagueoflegends.com";

    private String dataDragonLocale = "fr_FR";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(10);

    private final Quota quota = new Quota();

    private final Cache cache = new Cache();

    private final Ingest ingest = new Ingest();

    public String regionalBaseUrl() {
        return "https://" + region + ".api.riotgames.com";
    }

    public String platformBaseUrl() {
        return "https://" + platform + ".api.riotgames.com";
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Data
    public static class Quota {

        private int burstRequests = 20;

        private Duration burstWindow = Duration.ofSeconds(1);

        private int sustainedRequests = 100;

        private Duration sustainedWindow = Duration.ofMinutes(2);

        private int safetyMargin = 2;

        private int maxRetriesOn429 = 3;

        private Duration maxRetryAfter = Duration.ofMinutes(3);

        private Duration acquireTimeout = Duration.ofMinutes(5);

        // Doit rester nettement sous le read-timeout du cœur (connector.riot.read-timeout, 5 s).
        private Duration interactiveTimeout = Duration.ofSeconds(2);

        private Duration bulkYield = Duration.ofSeconds(10);

        private int interactiveReserve = 10;

        private Duration interactiveReserveIdle = Duration.ofMinutes(1);
    }

    @Data
    public static class Ingest {

        private boolean enabled = true;

        private Duration pollInterval = Duration.ofSeconds(2);

        private int batchSize = 25;

        // Doit dépasser quota.acquireTimeout, sinon une tâche qui attend son quota se fait voler par le rattrapage des orphelines.
        private Duration lease = Duration.ofMinutes(15);

        private int maxAttempts = 5;

        private Duration retryBackoff = Duration.ofMinutes(1);

        private Duration quotaBackoff = Duration.ofSeconds(30);

        private Duration idleBackoff = Duration.ofMinutes(5);
    }

    @Data
    public static class Cache {

        // Une partie peut apparaître dans l'historique avec du retard.
        private Duration historyOverlap = Duration.ofHours(1);

        private Duration historyFreshness = Duration.ofMinutes(15);

        private Duration historyDepth = Duration.ofDays(365);

        private Duration rankingTtl = Duration.ofHours(1);

        private Duration masteryTtl = Duration.ofHours(6);

        private Duration gameVersionTtl = Duration.ofHours(6);

        // Maximum Riot : 100.
        private int idPageSize = 100;

        private int maxIdPages = 50;

        private int maxDetailsPerCall = 60;
    }
}
