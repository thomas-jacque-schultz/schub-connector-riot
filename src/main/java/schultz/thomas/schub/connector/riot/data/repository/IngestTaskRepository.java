package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;

import java.util.List;
import java.util.Optional;

public interface IngestTaskRepository extends MongoRepository<IngestTask, String> {

    long countByState(IngestTaskState state);

    long countByPuuidAndState(String puuid, IngestTaskState state);

    /**
     * Ce qui sera servi avant une priorité donnée — l'ouvrier est unique et trie par priorité
     * décroissante, donc ce compte est le nombre d'appels qui séparent la file de cette tâche.
     */
    long countByStateAndPriorityGreaterThanEqual(IngestTaskState state, long priority);

    /** La tâche de ce joueur qui sera servie en dernier : c'est elle qui date sa fin. */
    Optional<IngestTask> findFirstByPuuidAndStateOrderByPriorityAsc(String puuid, IngestTaskState state);

    List<IngestTask> findByState(IngestTaskState state);
}
