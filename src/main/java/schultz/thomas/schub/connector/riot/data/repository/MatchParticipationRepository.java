package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MatchParticipationRepository extends MongoRepository<MatchParticipation, String> {

    List<MatchParticipation> findByPuuidOrderByStartedAtDesc(String puuid);

    List<MatchParticipation> findByPuuidAndStartedAtGreaterThanEqualOrderByStartedAtDesc(
            String puuid, Instant since);

    List<MatchParticipation> findByMatchIdIn(Collection<String> matchIds);

    long countByPuuid(String puuid);
}
