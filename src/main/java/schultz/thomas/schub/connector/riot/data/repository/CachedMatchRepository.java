package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import schultz.thomas.schub.connector.riot.data.model.CachedMatch;

import java.util.Collection;
import java.util.List;

/** Le cache permanent des parties. Aucune méthode de purge : c'est volontaire. */
public interface CachedMatchRepository extends MongoRepository<CachedMatch, String> {

    List<CachedMatch> findByMatchIdIn(Collection<String> matchIds);

    /**
     * Les identifiants déjà stockés, <strong>sans</strong> le JSON brut.
     *
     * <p>Chaque partie pèse près de 80 ko : savoir si mille parties sont connues chargerait
     * des dizaines de mégaoctets pour n'en lire que la clé. Les autres champs du record
     * reviennent à {@code null} — cette méthode ne sert qu'à tester l'appartenance.</p>
     */
    @Query(value = "{ '_id': { $in: ?0 } }", fields = "{ '_id': 1 }")
    List<CachedMatch> findStoredIds(Collection<String> matchIds);
}
