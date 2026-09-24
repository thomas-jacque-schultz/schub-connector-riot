package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.MatchSeat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MatchParticipationRepository extends MongoRepository<MatchParticipation, String> {

    List<MatchParticipation> findByPuuidOrderByStartedAtDesc(String puuid);

    List<MatchParticipation> findByPuuidAndStartedAtGreaterThanEqualOrderByStartedAtDesc(
            String puuid, Instant since);

    List<MatchParticipation> findByMatchIdIn(Collection<String> matchIds);

    // Le reste de la ligne n'est pas lu : l'entité complète ne peut pas en naître (ses primitifs resteraient nuls).
    @Query(value = "{ 'matchId': { $in: ?0 } }",
            fields = "{ 'puuid': 1, 'matchId': 1, 'side': 1, 'position': 1, 'championId': 1 }")
    List<MatchSeat> findSeatsByMatchIdIn(Collection<String> matchIds);

    long countByPuuid(String puuid);

    boolean existsByProjectionVersionNot(int projectionVersion);
}
