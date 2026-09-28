package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.api.dto.IngestPauseStatus;
import schultz.thomas.schub.connector.riot.data.model.IngestPauseSetting;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.repository.IngestPauseSettingRepository;

import java.time.Clock;
import java.util.Optional;

// Toute la file s'arrête, voie prioritaire comprise, en prod comme en dev : le temps d'une maintenance de la base.
// Les tâches déjà prises finissent ; les autres attendent la reprise, intactes. La pause survit à un redémarrage.
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestPause {

    private final IngestPauseSettingRepository settings;
    private final MongoTemplate mongo;
    private final Clock clock;

    public boolean paused() {
        return courant().map(IngestPauseSetting::paused).orElse(false);
    }

    public IngestPauseStatus status() {
        Optional<IngestPauseSetting> reglage = courant();
        return new IngestPauseStatus(reglage.map(IngestPauseSetting::paused).orElse(false),
                reglage.map(IngestPauseSetting::updatedAt).orElse(null), running());
    }

    public IngestPauseStatus set(boolean paused) {
        settings.save(new IngestPauseSetting(IngestPauseSetting.CURRENT, paused, clock.instant()));
        log.warn("Ingest {}.", paused ? "mis en pause" : "repris");
        return status();
    }

    // Bail encore valable : un ouvrier est en route. Un bail expiré est celui d'un ouvrier mort, il ne compte pas.
    public long running() {
        return mongo.count(Query.query(Criteria.where("state").is(IngestTaskState.RUNNING)
                .and("leaseUntil").gt(clock.instant())), IngestTask.class);
    }

    private Optional<IngestPauseSetting> courant() {
        return settings.findById(IngestPauseSetting.CURRENT);
    }
}
