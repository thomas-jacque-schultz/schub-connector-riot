package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.CachedMatch;

import java.util.Collection;
import java.util.List;

/** Le cache permanent des parties. Aucune méthode de purge : c'est volontaire. */
public interface CachedMatchRepository extends MongoRepository<CachedMatch, String> {

    List<CachedMatch> findByMatchIdIn(Collection<String> matchIds);
}
