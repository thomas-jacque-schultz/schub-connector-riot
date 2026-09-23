package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;

import java.util.Collection;
import java.util.List;

public interface MatchEarlyStatsRepository extends MongoRepository<MatchEarlyStats, String> {

    List<MatchEarlyStats> findByMatchIdIn(Collection<String> matchIds);
}
