package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import schultz.thomas.schub.connector.riot.data.model.PuuidCheck;

import java.time.Instant;
import java.util.List;

public interface PuuidCheckRepository extends MongoRepository<PuuidCheck, String> {

    List<PuuidCheck> findByValidFalseAndCheckedAtGreaterThanEqual(Instant since);
}
