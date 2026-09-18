package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.CachedGameVersion;

public interface CachedGameVersionRepository extends MongoRepository<CachedGameVersion, String> {
}
