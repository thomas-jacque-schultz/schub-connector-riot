package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.PlayerMatchRef;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PlayerMatchRefRepository extends MongoRepository<PlayerMatchRef, String> {

    List<PlayerMatchRef> findByPuuidAndPlayedAtGreaterThanEqualOrderByPlayedAtDesc(String puuid, Instant since);

    /**
     * Les parties dont on connaît l'id mais pas encore la date, faute d'avoir récupéré leur
     * détail. Elles sont jointes à toute lecture filtrée par date : les écarter reviendrait à
     * cacher exactement ce qui manque.
     */
    List<PlayerMatchRef> findByPuuidAndPlayedAtIsNull(String puuid);

    List<PlayerMatchRef> findByPuuidOrderByPlayedAtDesc(String puuid);

    List<PlayerMatchRef> findByMatchIdIn(Collection<String> matchIds);
}
