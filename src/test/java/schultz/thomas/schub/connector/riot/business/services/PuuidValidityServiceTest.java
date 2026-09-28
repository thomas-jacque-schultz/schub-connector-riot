package schultz.thomas.schub.connector.riot.business.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.StalePuuidException;
import schultz.thomas.schub.connector.riot.business.ingest.IngestQueue;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.PuuidCheck;
import schultz.thomas.schub.connector.riot.data.repository.PuuidCheckRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PuuidValidityServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-28T12:00:00Z");

    private final PuuidCheckRepository checks = mock(PuuidCheckRepository.class);
    private final RiotApiClient riot = mock(RiotApiClient.class);
    private final IngestQueue queue = mock(IngestQueue.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private PuuidValidityService service;

    @BeforeEach
    void setUp() {
        service = new PuuidValidityService(checks, riot, queue, mongo, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Un puuid déjà su refusé est rendu tout de suite ; un inconnu part en vérification")
    void verifieEnFile() {
        when(checks.findAllById(any())).thenReturn(List.of(
                new PuuidCheck("refuse", false, MAINTENANT.minus(Duration.ofDays(1))),
                new PuuidCheck("valide", true, MAINTENANT.minus(Duration.ofDays(1)))));

        assertThat(service.check(List.of("refuse", "valide", "inconnu"))).containsExactly("refuse");
        verify(queue).enqueue(IngestTaskType.PUUID_CHECK, "inconnu", "inconnu", 0);
        verify(queue, never()).enqueue(eq(IngestTaskType.PUUID_CHECK), eq("valide"), anyString(), anyLong());
    }

    @Test
    @DisplayName("Riot refuse le puuid : il est noté périmé et oublié de l'index")
    void refusParRiot() {
        when(riot.accountByPuuid("p")).thenThrow(new StalePuuidException("p", "HTTP 400"));

        service.verify("p");

        verify(checks).save(new PuuidCheck("p", false, MAINTENANT));
    }
}
