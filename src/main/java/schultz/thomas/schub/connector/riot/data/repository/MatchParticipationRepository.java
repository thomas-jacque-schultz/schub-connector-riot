package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MatchParticipationRepository extends MongoRepository<MatchParticipation, String> {

    List<MatchParticipation> findByPuuidOrderByStartedAtDesc(String puuid);

    List<MatchParticipation> findByPuuidAndStartedAtGreaterThanEqualOrderByStartedAtDesc(
            String puuid, Instant since);

    List<MatchParticipation> findByMatchIdIn(Collection<String> matchIds);

    // Qui jouait quoi, sans le reste de la ligne : de quoi placer les dix joueurs d'une partie.
    @Query(value = "{ 'matchId': { $in: ?0 } }",
            fields = "{ 'puuid': 1, 'matchId': 1, 'side': 1, 'position': 1, 'championId': 1 }")
    List<MatchParticipation> findSeatsByMatchIdIn(Collection<String> matchIds);

    long countByPuuid(String puuid);

    boolean existsByProjectionVersionNot(int projectionVersion);
}
