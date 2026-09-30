package schultz.thomas.schub.connector.riot.business.services;

import com.mongodb.client.ListCollectionsIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;

import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.CachedTimelineDigest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiotCollectionsTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MongoDatabase db = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    private final ListCollectionsIterable<Document> listing = mock(ListCollectionsIterable.class);
    private final RiotCollections collections = new RiotCollections(mongo);

    @BeforeEach
    void setUp() {
        MongoConverter converter = mock(MongoConverter.class);
        MongoMappingContext contexte = new MongoMappingContext();
        contexte.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        doReturn(contexte).when(converter).getMappingContext();
        when(mongo.getConverter()).thenReturn(converter);
        when(mongo.indexOps(any(Class.class))).thenReturn(mock(IndexOperations.class));
        when(mongo.getDb()).thenReturn(db);
        when(mongo.getCollectionName(any())).thenAnswer(appel -> ((Class<?>) appel.getArgument(0)).getSimpleName());
        when(db.listCollections()).thenReturn(listing);
        when(listing.filter(any(Bson.class))).thenReturn(listing);
    }

    @Test
    @DisplayName("Une collection est recréée avec le compresseur zstd, index compris")
    void recreeEnZstd() {
        collections.recree(CachedMatch.class);

        ArgumentCaptor<CreateCollectionOptions> options = ArgumentCaptor.forClass(CreateCollectionOptions.class);
        verify(mongo).dropCollection(CachedMatch.class);
        verify(db).createCollection(eq("CachedMatch"), options.capture());
        assertThat(options.getValue().getStorageEngineOptions().toBsonDocument().toJson()).contains("block_compressor=zstd");
    }

    @Test
    @DisplayName("Au démarrage, une collection pleine n'est jamais recréée, même en snappy")
    void neToucheJamaisUneCollectionPleine() {
        when(listing.first()).thenReturn(new Document("name", "x"));
        when(mongo.exists(any(Query.class), any(Class.class))).thenReturn(true);

        collections.afterPropertiesSet();

        verify(db, never()).createCollection(any(String.class), any(CreateCollectionOptions.class));
        verify(mongo, never()).dropCollection(any(Class.class));
    }

    @Test
    @DisplayName("Au démarrage, une collection vide en snappy est recréée, une collection déjà en zstd est laissée")
    void recreeLesVidesNonCompressees() {
        Document zstd = new Document("options", new Document("storageEngine",
                new Document("wiredTiger", new Document("configString", "block_compressor=zstd"))));
        when(listing.first()).thenReturn(zstd);
        when(mongo.exists(any(Query.class), any(Class.class))).thenReturn(false);

        assertThat(collections.compressee(CachedTimelineDigest.class)).isTrue();
        collections.afterPropertiesSet();
        verify(db, never()).createCollection(any(String.class), any(CreateCollectionOptions.class));

        when(listing.first()).thenReturn(new Document("name", "x"));
        collections.afterPropertiesSet();
        RiotDataPurge.PURGEES.forEach(type -> verify(mongo).dropCollection(eq(type)));
    }
}
