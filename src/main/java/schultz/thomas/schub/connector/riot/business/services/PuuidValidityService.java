package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.StalePuuidException;
import schultz.thomas.schub.connector.riot.business.ingest.IngestQueue;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.model.PuuidCheck;
import schultz.thomas.schub.connector.riot.data.repository.PuuidCheckRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Un puuid refusé par Riot l'est pour de bon (autre clé) : on l'oublie au lieu de le réessayer à chaque passage.
@Slf4j
@Service
@RequiredArgsConstructor
public class PuuidValidityService {

    private static final Duration VERIFICATION_VALABLE = Duration.ofDays(7);
    private static final List<IngestTaskType> TACHES_DU_JOUEUR = List.of(IngestTaskType.PLAYER_IDS,
            IngestTaskType.PLAYER_PREVIEW, IngestTaskType.PLAYER_PREVIEW_SLOW, IngestTaskType.PLAYER_RANK,
            IngestTaskType.PLAYER_MASTERY);

    private final PuuidCheckRepository checks;
    private final RiotApiClient riotApiClient;
    private final IngestQueue queue;
    private final MongoTemplate mongo;
    private final Clock clock;

    public void markStale(String puuid) {
        checks.save(new PuuidCheck(puuid, false, clock.instant()));
        mongo.remove(Query.query(Criteria.where("_id").is(puuid)), KnownAccount.class);
        mongo.remove(Query.query(Criteria.where("_id").is(puuid)), PlayerHistoryCursor.class);
        mongo.remove(Query.query(Criteria.where("key").is(puuid).and("type").in(TACHES_DU_JOUEUR)),
                IngestTask.class);
        log.info("Puuid refusé par Riot, relevé avec une autre clé : oublié.");
    }

    public List<String> staleSince(Instant since) {
        return checks.findByValidFalseAndCheckedAtGreaterThanEqual(since).stream().map(PuuidCheck::puuid).toList();
    }

    // Les puuid pas vérifiés depuis une semaine partent en file (un appel à Riot chacun) ; staleSince en rendra le verdict.
    public List<String> check(Collection<String> puuids) {
        Instant now = clock.instant();
        Map<String, PuuidCheck> connus = checks.findAllById(new LinkedHashSet<>(puuids)).stream()
                .collect(Collectors.toMap(PuuidCheck::puuid, Function.identity()));

        List<String> perimes = new ArrayList<>();
        for (String puuid : new LinkedHashSet<>(puuids)) {
            PuuidCheck connu = connus.get(puuid);
            if (connu != null && connu.checkedAt().plus(VERIFICATION_VALABLE).isAfter(now)) {
                if (!connu.valid()) {
                    perimes.add(puuid);
                }
                continue;
            }
            queue.enqueue(IngestTaskType.PUUID_CHECK, puuid, puuid, 0);
        }
        return perimes;
    }

    public void verify(String puuid) {
        try {
            if (riotApiClient.accountByPuuid(puuid).isEmpty()) {
                markStale(puuid);
                return;
            }
            checks.save(new PuuidCheck(puuid, true, clock.instant()));
        } catch (StalePuuidException refuse) {
            markStale(puuid);
        }
    }
}
