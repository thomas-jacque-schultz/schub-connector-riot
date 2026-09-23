package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import schultz.thomas.schub.connector.riot.data.model.CachedMatch;

import java.util.Collection;
import java.util.List;

public interface CachedMatchRepository extends MongoRepository<CachedMatch, String> {

    List<CachedMatch> findByMatchIdIn(Collection<String> matchIds);

    // Projection : les autres champs reviennent à null, ne sert qu'à tester l'appartenance.
    @Query(value = "{ '_id': { $in: ?0 } }", fields = "{ '_id': 1 }")
    List<CachedMatch> findStoredIds(Collection<String> matchIds);
}
