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

/**
 * Ce qui écrit dans {@code riot_known_account} : la seule porte de l'index.
 *
 * <h2>Une observation, pas une mise à jour</h2>
 *
 * <p>Écrire n'est jamais « ce compte s'appelle X » mais « le {@code <date>}, ce compte
 * s'appelait X ». Une observation n'est retenue que si elle est plus récente que celle en
 * place, ce qui rend l'écriture idempotente et l'ordre des observations sans importance — une
 * partie de 2024 reprojetée après une résolution d'aujourd'hui ne peut pas périmer l'index.</p>
 *
 * <h2>Reconstruction</h2>
 *
 * <p>La part venue des participations se reprojette depuis {@code riot_participation}, qui est
 * elle-même reconstructible depuis {@code riot_match} — donc sans un appel à Riot. La part venue
 * des résolutions, elle, <strong>n'existe nulle part ailleurs</strong> : c'est pourquoi la
 * reconstruction ne purge pas, elle rejoue. Purger perdrait les comptes vérifiés qui n'ont
 * jamais été croisés en partie, c'est-à-dire exactement ceux que l'index sert à retenir.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KnownAccountIndex {

    private static final int LOT = 500;

    private final KnownAccountRepository accounts;
    private final MongoTemplate mongo;
    private final Clock clock;

    /** Riot vient de confirmer ce Riot ID : l'observation est datée de maintenant. */
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

    /** Rejoue les participations dans l'index. Ne purge pas — voir la note de classe. */
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

    /**
     * @param observedAt la date de ce qui a été observé — celle de la partie, celle de l'appel —
     *                   jamais celle de l'écriture.
     */
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
