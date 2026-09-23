package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.StoredReference;

import java.util.List;
import java.util.Optional;

public interface StoredReferenceRepository extends MongoRepository<StoredReference, String> {

    Optional<StoredReference> findFirstByScopeAndPositionOrderByComputedAtDesc(String scope, String position);

    List<StoredReference> findByScopeAndPositionAndPatchesContainingOrderByComputedAtDesc(String scope, String position,
                                                                                          String patch);
}
