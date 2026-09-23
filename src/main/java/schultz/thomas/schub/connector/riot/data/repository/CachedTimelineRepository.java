package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import schultz.thomas.schub.connector.riot.data.model.CachedTimeline;

import java.util.Collection;
import java.util.List;

public interface CachedTimelineRepository extends MongoRepository<CachedTimeline, String> {

    List<CachedTimeline> findByMatchIdIn(Collection<String> matchIds);

    @Query(value = "{ '_id': { $in: ?0 } }", fields = "{ '_id': 1 }")
    List<CachedTimeline> findStoredIds(Collection<String> matchIds);
}
