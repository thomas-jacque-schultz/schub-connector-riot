package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;

import java.util.Collection;
import java.util.List;

public interface MatchEarlyStatsRepository extends MongoRepository<MatchEarlyStats, String> {

    List<MatchEarlyStats> findByMatchIdIn(Collection<String> matchIds);

    @Query(value = "{ 'version': { $ne: ?0 } }", fields = "{ '_id': 1 }")
    List<MatchEarlyStats> findOutdatedIds(int version);
}
