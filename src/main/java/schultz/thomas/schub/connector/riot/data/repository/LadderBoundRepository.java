package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.LadderBound;

public interface LadderBoundRepository extends MongoRepository<LadderBound, String> {
}
