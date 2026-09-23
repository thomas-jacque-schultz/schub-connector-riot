package schultz.thomas.schub.connector.riot.business.search;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;

import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.KnownAccountRepository;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnownAccountIndexTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-21T10:00:00Z");
    private static final Instant HIER = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant IL_Y_A_DEUX_ANS = Instant.parse("2024-09-20T10:00:00Z");

    @Mock private KnownAccountRepository accounts;
    @Mock private MongoTemplate mongo;

    private KnownAccountIndex index;

    @BeforeEach
    void setUp() {
        index = new KnownAccountIndex(accounts, mongo, new TestClock(MAINTENANT));
    }

    private KnownAccountIndex.Observation partie(String puuid, String gameName, Instant quand) {
        return new KnownAccountIndex.Observation(puuid, gameName, "EUW", quand,
                KnownAccountSource.PARTICIPATION);
    }

    @SuppressWarnings("unchecked")
    private List<KnownAccount> ecrits() {
        ArgumentCaptor<Iterable<KnownAccount>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(accounts).saveAll(captor.capture());
        List<KnownAccount> ecrits = new ArrayList<>();
        captor.getValue().forEach(ecrits::add);
        return ecrits;
    }

    @Test
    @DisplayName("une résolution écrit l'identité du jour, datée de l'appel")
    void enregistreUneResolution() {
        when(accounts.findAllById(anyIterable())).thenReturn(List.of());

        assertThat(index.observeResolution("p1", "Thomas", "EUW")).isTrue();

        KnownAccount ecrit = ecrits().getFirst();
        assertThat(ecrit.puuid()).isEqualTo("p1");
        assertThat(ecrit.riotId()).isEqualTo("Thomas#EUW");
        assertThat(ecrit.searchName()).isEqualTo("thomas");
        assertThat(ecrit.observedAt()).isEqualTo(MAINTENANT);
        assertThat(ecrit.source()).isEqualTo(KnownAccountSource.RESOLUTION);
    }

    @Test
    @DisplayName("une observation plus ancienne que celle en place n'écrase rien")
    void nEcrasePasAvecUneObservationPlusAncienne() {
        when(accounts.findAllById(anyIterable())).thenReturn(List.of(
                new KnownAccount("p1", "Thomas", "EUW", "thomas", MAINTENANT,
                        KnownAccountSource.RESOLUTION)));

        assertThat(index.observeAll(List.of(partie("p1", "AncienPseudo", IL_Y_A_DEUX_ANS)))).isZero();
        verify(accounts, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("d'un même lot, seule la plus récente observation d'un compte est retenue")
    void neGardeQueLaPlusRecenteDUnLot() {
        when(accounts.findAllById(anyIterable())).thenReturn(List.of());

        index.observeAll(List.of(partie("p1", "Ancien", HIER), partie("p1", "Recent", MAINTENANT)));

        assertThat(ecrits()).singleElement()
                .extracting(KnownAccount::gameName).isEqualTo("Recent");
    }

    @Test
    @DisplayName("une observation sans puuid, sans date ou sans pseudo n'entre pas dans l'index")
    void ecarteLesObservationsInexploitables() {
        assertThat(index.observeAll(List.of(
                partie(null, "Thomas", MAINTENANT),
                partie("p1", "Thomas", null),
                partie("p2", "   ", MAINTENANT)))).isZero();
        verify(accounts, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("la reconstruction rejoue les participations sans purger les comptes vérifiés")
    void reconstruitSansPurger() {
        when(mongo.aggregateStream(any(Aggregation.class), eq(MatchParticipation.class),
                eq(Document.class)))
                .thenReturn(Stream.of(new Document("_id", "p1")
                        .append("gameName", "Thomas")
                        .append("tagLine", "EUW")
                        .append("observedAt", Date.from(HIER))));
        when(accounts.findAllById(anyIterable())).thenReturn(List.of());

        assertThat(index.rebuildFromParticipations().accountsWritten()).isEqualTo(1);

        verify(accounts, never()).deleteAll();
        assertThat(ecrits().getFirst().source()).isEqualTo(KnownAccountSource.PARTICIPATION);
    }
}
