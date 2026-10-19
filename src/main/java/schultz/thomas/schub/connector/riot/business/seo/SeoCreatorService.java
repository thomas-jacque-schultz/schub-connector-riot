package schultz.thomas.schub.connector.riot.business.seo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.business.model.PlayerIdentity;
import schultz.thomas.schub.connector.riot.business.model.SeoCreators;
import schultz.thomas.schub.connector.riot.business.search.SearchName;
import schultz.thomas.schub.connector.riot.business.services.PlayerIdentityService;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.SeoCreator;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Riot ne sait pas lister les créateurs de contenu : la liste est tenue à la main (Schub#91).
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoCreatorService {

    private final MongoTemplate mongo;
    private final PlayerIdentityService identities;
    private final IngestService ingest;
    private final Clock clock;

    public SeoCreators current() {
        return new SeoCreators(all().stream().map(SeoCreator::riotId).sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                List.of());
    }

    public Set<String> puuids() {
        Set<String> puuids = new HashSet<>();
        for (SeoCreator createur : all()) {
            mongo.find(Query.query(Criteria.where("searchName").is(SearchName.fold(createur.gameName()))),
                            KnownAccount.class).stream()
                    .filter(compte -> createur.riotId().equalsIgnoreCase(compte.riotId()))
                    .forEach(compte -> puuids.add(compte.puuid()));
        }
        return puuids;
    }

    // Un créateur ajouté voit son historique relevé : sans parties, sa page ne serait pas indexée.
    public SeoCreators replace(List<String> riotIds) {
        Map<String, SeoCreator> existants = all().stream()
                .collect(Collectors.toMap(SeoCreator::key, c -> c, (a, b) -> a));
        Map<String, SeoCreator> retenus = new LinkedHashMap<>();
        List<String> inconnus = new ArrayList<>();
        for (String brut : riotIds) {
            String riotId = brut == null ? "" : brut.trim();
            int diese = riotId.lastIndexOf('#');
            if (diese <= 0 || diese == riotId.length() - 1) {
                if (!riotId.isEmpty()) {
                    inconnus.add(riotId);
                }
                continue;
            }
            SeoCreator connu = existants.get(riotId.toLowerCase(Locale.ROOT));
            if (connu != null) {
                retenus.put(connu.key(), connu);
                continue;
            }
            try {
                PlayerIdentity identite = identities.resolve(riotId.substring(0, diese), riotId.substring(diese + 1));
                SeoCreator nouveau = SeoCreator.of(identite.gameName(), identite.tagLine(), clock.instant());
                retenus.put(nouveau.key(), nouveau);
                ingest.enqueuePlayer(identite.puuid());
            } catch (RiotResourceNotFoundException e) {
                inconnus.add(riotId);
            }
        }
        mongo.remove(new Query(), SeoCreator.class);
        retenus.values().forEach(mongo::save);
        log.info("Créateurs de contenu : {} retenus, {} inconnus", retenus.size(), inconnus.size());
        return new SeoCreators(current().creators(), inconnus);
    }

    private List<SeoCreator> all() {
        return mongo.findAll(SeoCreator.class);
    }
}
