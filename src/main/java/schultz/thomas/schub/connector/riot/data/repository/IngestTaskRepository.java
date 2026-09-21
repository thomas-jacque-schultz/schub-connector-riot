package schultz.thomas.schub.connector.riot.data.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;

import java.util.List;

public interface IngestTaskRepository extends MongoRepository<IngestTask, String> {

    long countByState(IngestTaskState state);

    long countByPuuidAndState(String puuid, IngestTaskState state);

    List<IngestTask> findByState(IngestTaskState state);
}
