package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.PlayerMatchRef;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerMatchRefRepository extends MongoRepository<PlayerMatchRef, String> {

    List<PlayerMatchRef> findByPuuidAndPlayedAtGreaterThanEqualOrderByPlayedAtDesc(String puuid, Instant since);

    List<PlayerMatchRef> findByPuuidAndPlayedAtIsNull(String puuid);

    Optional<PlayerMatchRef> findFirstByPuuidAndPlayedAtNotNullOrderByPlayedAtDesc(String puuid);

    List<PlayerMatchRef> findByMatchIdIn(Collection<String> matchIds);

    long countByPuuid(String puuid);
}
