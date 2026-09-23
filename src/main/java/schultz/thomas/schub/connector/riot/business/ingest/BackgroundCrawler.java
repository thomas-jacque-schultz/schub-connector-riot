package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import schultz.thomas.schub.connector.riot.api.dto.CrawlerStatus;
import schultz.thomas.schub.connector.riot.business.exceptions.CrawlerLockedException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CrawlerSetting;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.repository.CrawlerSettingRepository;
import schultz.thomas.schub.connector.riot.data.repository.IngestTaskRepository;
import schultz.thomas.schub.connector.riot.data.repository.KnownAccountRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerHistoryCursorRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Comptes croisés les plus récemment d'abord : ce sont ceux qui rejoindront le plus probablement Schub.
@Slf4j
@Component
@RequiredArgsConstructor
public class BackgroundCrawler {

    private static final Duration MESURE_VALIDE = Duration.ofMinutes(1);

    private final RiotProperties properties;
    private final CrawlerSettingRepository settings;
    private final IngestTaskRepository tasks;
    private final KnownAccountRepository accounts;
    private final PlayerHistoryCursorRepository cursors;
    private final IngestQueue queue;
    private final MongoTemplate mongo;
    private final Clock clock;

    private volatile Instant lastRoundAt;
    private volatile int lastRoundAccounts;
    private volatile Mesure mesure;

    private record Mesure(long octets, Instant prise) {
    }

    public boolean enabled() {
        RiotProperties.Crawler config = properties.getCrawler();
        if (!config.isSwitchable()) {
            return true;
        }
        return settings.findById(CrawlerSetting.CURRENT)
                .map(CrawlerSetting::enabled)
                .orElse(config.isEnabledByDefault());
    }

    // Activée et sous le seuil de volume : c'est ce qui autorise l'ouvrier à servir ses tâches.
    public boolean active() {
        return enabled() && databaseBytes() <= properties.getCrawler().getStorageAlertBytes();
    }

    public CrawlerStatus toggle(boolean enabled) {
        if (!properties.getCrawler().isSwitchable()) {
            throw new CrawlerLockedException();
        }
        settings.save(new CrawlerSetting(CrawlerSetting.CURRENT, enabled, clock.instant()));
        log.info("Collecte de fond {}.", enabled ? "activée" : "désactivée");
        return status();
    }

    public void round() {
        if (!enabled() || !properties.getIngest().isEnabled()) {
            return;
        }
        RiotProperties.Crawler config = properties.getCrawler();
        long octets = databaseBytes();
        if (octets > config.getStorageAlertBytes()) {
            log.warn("Collecte de fond suspendue : la base pèse {} octets, au-delà du seuil de {}.",
                    octets, config.getStorageAlertBytes());
            return;
        }
        if (backgroundPending() >= config.getBacklog()) {
            return;
        }

        int empiles = 0;
        for (String puuid : aRelever(config)) {
            if (queue.enqueue(IngestTaskType.PLAYER_IDS, puuid, puuid, IngestTask.BACKGROUND_PLAYER_PRIORITY)) {
                empiles++;
            }
        }
        if (empiles > 0) {
            lastRoundAt = clock.instant();
            lastRoundAccounts = empiles;
            log.info("Collecte de fond : {} comptes empilés.", empiles);
        }
    }

    public CrawlerStatus status() {
        RiotProperties.Crawler config = properties.getCrawler();
        boolean enabled = enabled();
        long octets = databaseBytes();
        boolean alerte = octets > config.getStorageAlertBytes();
        return new CrawlerStatus(config.isSwitchable(), enabled, enabled && !alerte, accounts.count(),
                cursors.count(), backgroundPending(), octets, config.getStorageAlertBytes(), alerte,
                lastRoundAt, lastRoundAccounts);
    }

    private long backgroundPending() {
        return tasks.countByState(IngestTaskState.PENDING)
                - tasks.countByStateAndPriorityGreaterThanEqual(IngestTaskState.PENDING, 0);
    }

    private List<String> aRelever(RiotProperties.Crawler config) {
        Date seuil = Date.from(clock.instant().minus(config.getRefreshAfter()));
        List<Document> pipeline = List.of(
                new Document("$sort", new Document("observedAt", -1)),
                new Document("$lookup", new Document("from", "riot_player_cursor")
                        .append("localField", "_id").append("foreignField", "_id").append("as", "cursor")),
                new Document("$match", new Document("$or", List.of(
                        new Document("cursor", new Document("$size", 0)),
                        new Document("cursor.lastSyncStartedAt", new Document("$lt", seuil))))),
                new Document("$limit", config.getAccountsPerRound()),
                new Document("$project", new Document("_id", 1)));
        return mongo.getCollection("riot_known_account").aggregate(pipeline).allowDiskUse(true)
                .map(document -> document.getString("_id"))
                .into(new ArrayList<>());
    }

    // dbStats parcourt toutes les collections ; l'ouvrier demande le volume toutes les deux secondes.
    private long databaseBytes() {
        Instant maintenant = clock.instant();
        Mesure derniere = mesure;
        if (derniere == null || derniere.prise().plus(MESURE_VALIDE).isBefore(maintenant)) {
            derniere = new Mesure(mesureVolume(), maintenant);
            mesure = derniere;
        }
        return derniere.octets();
    }

    private long mesureVolume() {
        Document stats = mongo.getDb().runCommand(new Document("dbStats", 1));
        Object total = stats.get("totalSize");
        if (total instanceof Number n) {
            return n.longValue();
        }
        return nombre(stats, "storageSize") + nombre(stats, "indexSize");
    }

    private static long nombre(Document document, String champ) {
        return document.get(champ) instanceof Number n ? n.longValue() : 0L;
    }
}
