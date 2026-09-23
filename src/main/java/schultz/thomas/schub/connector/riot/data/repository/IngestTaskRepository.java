package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngestTaskRepository extends MongoRepository<IngestTask, String> {

    long countByState(IngestTaskState state);

    long countByPuuidAndState(String puuid, IngestTaskState state);

    long countByStateAndPriorityGreaterThanEqual(IngestTaskState state, long priority);

    Optional<IngestTask> findFirstByPuuidAndStateOrderByPriorityAsc(String puuid, IngestTaskState state);

    List<IngestTask> findByState(IngestTaskState state);

    long countByTypeAndKeyStartingWithAndStateIn(IngestTaskType type, String prefix, Collection<IngestTaskState> states);
}
