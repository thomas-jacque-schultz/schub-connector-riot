package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import schultz.thomas.schub.connector.riot.data.model.IngestPauseSetting;

public interface IngestPauseSettingRepository extends MongoRepository<IngestPauseSetting, String> {
}
