package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.StoredChampionReference;

import java.util.Optional;

public interface StoredChampionReferenceRepository extends MongoRepository<StoredChampionReference, String> {

    Optional<StoredChampionReference> findFirstByChampionIdOrderByComputedAtDesc(int championId);
}
