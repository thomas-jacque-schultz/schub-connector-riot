package schultz.thomas.schub.connector.riot.business.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.KnownAccountRebuildReport;
import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.KnownAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

// Une observation n'est retenue que si elle est plus récente : l'ordre des écritures est indifférent.
// La reconstruction rejoue sans purger : les résolutions n'existent nulle part ailleurs.
@Slf4j
@RequiredArgsConstructor
@Service
public class KnownAccountIndex {

    private static final int LOT = 500;

    private final KnownAccountRepository accounts;
    private final MongoTemplate mongo;
    private final Clock clock;

    public boolean observeResolution(String puuid, String gameName, String tagLine) {
        return observeAll(List.of(new Observation(puuid, gameName, tagLine, clock.instant(),
                KnownAccountSource.RESOLUTION))) > 0;
    }

    public int observeAll(Collection<Observation> observations) {
        Map<String, Observation> plusRecentes = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (observation.recevable()) {
                plusRecentes.merge(observation.puuid(), observation, Observation::laPlusRecente);
            }
        }
        if (plusRecentes.isEmpty()) {
            return 0;
        }

        Map<String, KnownAccount> enPlace = accounts.findAllById(plusRecentes.keySet()).stream()
                .collect(Collectors.toMap(KnownAccount::puuid, Function.identity(),
                        (premier, second) -> premier));

        List<KnownAccount> aEcrire = plusRecentes.values().stream()
                .filter(observation -> observation.apporteDuNeuf(enPlace.get(observation.puuid())))
                .map(Observation::toAccount)
                .toList();

        if (!aEcrire.isEmpty()) {
            accounts.saveAll(aEcrire);
        }
        return aEcrire.size();
    }

    public KnownAccountRebuildReport rebuildFromParticipations() {
        Instant startedAt = clock.instant();
        int lus = 0;
        int ecrits = 0;
        List<Observation> lot = new ArrayList<>(LOT);

        try (Stream<Document> comptes = mongo.aggregateStream(
                derniereIdentiteParPuuid(), MatchParticipation.class, Document.class)) {
            for (Document compte : (Iterable<Document>) comptes::iterator) {
                lus++;
                lot.add(toObservation(compte));
                if (lot.size() >= LOT) {
                    ecrits += observeAll(lot);
                    lot.clear();
                }
            }
        }
        ecrits += observeAll(lot);

        log.info("Index des comptes connus reprojeté : {} comptes lus, {} entrées écrites.", lus, ecrits);
        return new KnownAccountRebuildReport(lus, ecrits, startedAt, clock.instant());
    }

    private static Aggregation derniereIdentiteParPuuid() {
        return Aggregation.newAggregation(
                        Aggregation.sort(Sort.Direction.DESC, "startedAt"),
                        Aggregation.group("puuid")
                                .first("gameName").as("gameName")
                                .first("tagLine").as("tagLine")
                                .max("startedAt").as("observedAt"))
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
    }

    private static Observation toObservation(Document row) {
        Object observedAt = row.get("observedAt");
        return new Observation(
                row.getString("_id"),
                row.getString("gameName"),
                row.getString("tagLine"),
                observedAt instanceof Date date ? date.toInstant() : (Instant) observedAt,
                KnownAccountSource.PARTICIPATION);
    }

    public record Observation(String puuid, String gameName, String tagLine, Instant observedAt,
                              KnownAccountSource source) {

        boolean recevable() {
            return puuid != null && !puuid.isBlank() && observedAt != null
                    && SearchName.fold(gameName) != null;
        }

        Observation laPlusRecente(Observation autre) {
            return autre.observedAt().isAfter(observedAt) ? autre : this;
        }

        boolean apporteDuNeuf(KnownAccount enPlace) {
            return enPlace == null || enPlace.observedAt() == null
                    || observedAt.isAfter(enPlace.observedAt());
        }

        KnownAccount toAccount() {
            return new KnownAccount(puuid, gameName, tagLine, SearchName.fold(gameName),
                    observedAt, source);
        }
    }
}
