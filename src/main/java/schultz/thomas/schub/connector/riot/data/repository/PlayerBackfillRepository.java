package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import schultz.thomas.schub.connector.riot.data.model.PlayerBackfill;

public interface PlayerBackfillRepository extends MongoRepository<PlayerBackfill, String> {
}
