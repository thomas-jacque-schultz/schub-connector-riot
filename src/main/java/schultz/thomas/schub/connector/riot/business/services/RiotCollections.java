package schultz.thomas.schub.connector.riot.business.services;

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

// Le compresseur se fixe à la création d'une collection. Au démarrage (avant les ouvriers), seules les collections
// vides sont recréées ; les autres passent en zstd à la prochaine invalidation.
@Slf4j
@Component
@RequiredArgsConstructor
public class RiotCollections implements InitializingBean {

    static final String COMPRESSEUR = "block_compressor=zstd";

    private final MongoTemplate mongo;

    @Override
    public void afterPropertiesSet() {
        List<Class<?>> vides = RiotDataPurge.PURGEES.stream()
                .filter(type -> !compressee(type) && !mongo.exists(new Query(), type))
                .toList();
        vides.forEach(this::recree);
        if (!vides.isEmpty()) {
            log.info("{} collection(s) vide(s) recréée(s) en zstd.", vides.size());
        }
    }

    public void recree(Class<?> type) {
        String nom = mongo.getCollectionName(type);
        mongo.dropCollection(type);
        mongo.getDb().createCollection(nom, new CreateCollectionOptions()
                .storageEngineOptions(new Document("wiredTiger", new Document("configString", COMPRESSEUR))));
        IndexOperations index = mongo.indexOps(type);
        IndexResolver.create(mongo.getConverter().getMappingContext()).resolveIndexFor(type).forEach(index::ensureIndex);
    }

    boolean compressee(Class<?> type) {
        Document collection = mongo.getDb().listCollections()
                .filter(Filters.eq("name", mongo.getCollectionName(type))).first();
        if (collection == null) {
            return false;
        }
        Object config = collection.get("options", new Document())
                .get("storageEngine", new Document())
                .get("wiredTiger", new Document())
                .get("configString");
        return config instanceof String chaine && chaine.contains(COMPRESSEUR);
    }
}
