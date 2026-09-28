package schultz.thomas.schub.connector.riot.business.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import schultz.thomas.schub.connector.riot.api.dto.RiotDataPurgeReport;
import schultz.thomas.schub.connector.riot.business.exceptions.IngestNotPausedException;
import schultz.thomas.schub.connector.riot.business.ingest.IngestPause;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiotDataPurgeTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final IndexOperations index = mock(IndexOperations.class);
    private final IngestPause pause = mock(IngestPause.class);
    private final RiotDataPurge purge = new RiotDataPurge(mongo, pause, new TestClock(Instant.parse("2026-09-28T12:00:00Z")));

    @BeforeEach
    void setUp() {
        MongoConverter converter = mock(MongoConverter.class);
        MongoMappingContext contexte = new MongoMappingContext();
        contexte.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        doReturn(contexte).when(converter).getMappingContext();
        when(mongo.getConverter()).thenReturn(converter);
        when(mongo.indexOps(any(Class.class))).thenReturn(index);
        when(mongo.getCollectionName(any())).thenAnswer(appel -> {
            Class<?> type = appel.getArgument(0);
            return type.getAnnotation(Document.class).value();
        });
    }

    @Test
    @DisplayName("Chaque collection du connecteur est soit effacée, soit gardée : une nouvelle collection doit être rangée")
    void toutesLesCollectionsSontRangees() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));
        Set<String> documents = scanner.findCandidateComponents("schultz.thomas.schub.connector.riot").stream()
                .map(BeanDefinition::getBeanClassName).collect(Collectors.toSet());

        Set<String> rangees = new HashSet<>();
        RiotDataPurge.PURGEES.forEach(type -> rangees.add(type.getName()));
        RiotDataPurge.GARDEES.forEach(type -> rangees.add(type.getName()));

        assertThat(rangees).containsExactlyInAnyOrderElementsOf(documents);
    }

    @Test
    @DisplayName("L'effacement supprime les collections de la collecte, repose leurs index et ne touche pas aux réglages")
    void effaceEtReposeLesIndex() {
        when(pause.paused()).thenReturn(true);
        when(mongo.estimatedCount(CachedMatch.class)).thenReturn(74_000L);
        when(mongo.estimatedCount(KnownAccount.class)).thenReturn(478_000L);

        RiotDataPurgeReport rapport = purge.purge();

        RiotDataPurge.PURGEES.forEach(type -> verify(mongo).dropCollection(eq(type)));
        RiotDataPurge.GARDEES.forEach(type -> verify(mongo, never()).dropCollection(eq(type)));
        verify(index, atLeastOnce()).ensureIndex(any());
        assertThat(rapport.removed()).containsEntry("riot_match", 74_000L).containsEntry("riot_known_account", 478_000L);
        assertThat(rapport.total()).isEqualTo(552_000L);
    }

    @Test
    @DisplayName("Sans pause de l'ingest, rien n'est effacé")
    void exigeLaPause() {
        when(pause.paused()).thenReturn(false);

        assertThatThrownBy(purge::purge).isInstanceOf(IngestNotPausedException.class);

        verify(mongo, never()).dropCollection(any(Class.class));
    }

    @Test
    @DisplayName("En pause mais avec une tâche encore en route, rien n'est effacé")
    void attendLaFinDesTaches() {
        when(pause.paused()).thenReturn(true);
        when(pause.running()).thenReturn(2L);

        assertThatThrownBy(purge::purge).isInstanceOf(IngestNotPausedException.class).hasMessageContaining("2 tâche");

        verify(mongo, never()).dropCollection(any(Class.class));
    }
}
