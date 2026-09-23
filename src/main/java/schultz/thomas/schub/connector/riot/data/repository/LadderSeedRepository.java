package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.LadderSeed;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LadderSeedRepository extends MongoRepository<LadderSeed, String> {

    long countByGroupAndSampledAtGreaterThanEqual(String group, Instant since);

    List<LadderSeed> findByPuuidInAndSampledAtGreaterThanEqual(Collection<String> puuids, Instant since);
}
