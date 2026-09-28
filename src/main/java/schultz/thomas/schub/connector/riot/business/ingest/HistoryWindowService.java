package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.HistoryWindow;
import schultz.thomas.schub.connector.riot.business.exceptions.InvalidHistoryWindowException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.HistoryWindowSetting;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.repository.HistoryWindowSettingRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryWindowService {

    private final HistoryWindowSettingRepository settings;
    private final IngestQueue queue;
    private final MongoTemplate mongo;
    private final RiotProperties properties;
    private final Clock clock;

    public HistoryWindow current() {
        return settings.findById(HistoryWindowSetting.CURRENT)
                .map(setting -> new HistoryWindow(setting.maxGames(), setting.maxAgeDays(), setting.minGames()))
                .orElseGet(this::defaults);
    }

    public HistoryWindow update(HistoryWindow window) {
        if (window == null || !window.valid()) {
            throw new InvalidHistoryWindowException();
        }
        HistoryWindowSetting setting = new HistoryWindowSetting(HistoryWindowSetting.CURRENT, window.maxGames(),
                window.maxAgeDays(), window.minGames(), clock.instant(), null);
        settings.save(setting);
        log.info("Fenêtre de relevé : {} parties, {} jours, plancher de {}.",
                window.maxGames(), window.maxAgeDays(), window.minGames());
        prune(setting);
        return window;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void pruneIfNeeded() {
        HistoryWindowSetting setting = settings.findById(HistoryWindowSetting.CURRENT).orElseGet(() -> {
            HistoryWindow defaults = defaults();
            return new HistoryWindowSetting(HistoryWindowSetting.CURRENT, defaults.maxGames(),
                    defaults.maxAgeDays(), defaults.minGames(), clock.instant(), null);
        });
        if (!setting.pruned()) {
            prune(setting);
        }
    }

    // Les dates des parties en file sont inconnues tant que leur détail ne l'est pas : on abandonne les détails de fond
    // de chaque joueur et on relève de nouveau ses identifiants, cette fois dans la fenêtre.
    private void prune(HistoryWindowSetting setting) {
        Query fond = Query.query(Criteria.where("type").is(IngestTaskType.MATCH_DETAIL)
                .and("state").is(IngestTaskState.PENDING)
                .and("priority").lt(IngestTask.SAMPLING_OFFSET));
        List<String> joueurs = mongo.findDistinct(fond, "puuid", IngestTask.class, String.class);
        long abandonnees = mongo.remove(fond, IngestTask.class).getDeletedCount();

        if (!joueurs.isEmpty()) {
            mongo.remove(Query.query(Criteria.where("_id").in(joueurs)), PlayerHistoryCursor.class);
            joueurs.forEach(puuid -> queue.enqueue(IngestTaskType.PLAYER_IDS, puuid, puuid,
                    IngestTask.BACKGROUND_PLAYER_PRIORITY));
        }
        settings.save(setting.prunedAt(clock.instant()));
        log.info("File ramenée à la fenêtre de relevé : {} détails abandonnés, {} joueurs à relever de nouveau.",
                abandonnees, joueurs.size());
    }

    private HistoryWindow defaults() {
        RiotProperties.History config = properties.getHistory();
        return new HistoryWindow(config.getMaxGames(), config.getMaxAgeDays(), config.getMinGames());
    }
}
