package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.MatchRankSnapshot;

import java.util.Collection;
import java.util.List;

public interface MatchRankSnapshotRepository extends MongoRepository<MatchRankSnapshot, String> {

    List<MatchRankSnapshot> findByMatchIdIn(Collection<String> matchIds);
}
