package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.TimelineHabits;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Les signaux de timeline ne sont pas stockés : ils se recalculent partie par partie, d'où la borne sur le nombre de parties.
@Service
@RequiredArgsConstructor
public class TimelineHabitService {

    public static final Set<String> SIGNAUX = Set.of("isolatedDeaths", "firstDeathsInFights", "chainDeathShare",
            "groupedAtObjectivesShare", "objectivesCededWithoutTrade", "gankDeaths", "lostTradeDeaths",
            "outnumberedFights");
    static final int PARTIES_MAX = 30;
    private static final int LOT = 200;

    private final MongoTemplate mongo;
    private final MatchSignalsService signals;

    public TimelineHabits of(String puuid, Instant since) {
        List<String> parties = avecTimeline(puuid, since);
        Map<String, Double> sommes = new LinkedHashMap<>();
        Map<String, Integer> comptes = new LinkedHashMap<>();
        for (String matchId : parties) {
            signals.of(matchId).getOrDefault(puuid, Map.of()).forEach((cle, valeur) -> {
                if (SIGNAUX.contains(cle) && valeur != null) {
                    sommes.merge(cle, valeur, Double::sum);
                    comptes.merge(cle, 1, Integer::sum);
                }
            });
        }
        Map<String, Double> moyennes = new LinkedHashMap<>();
        sommes.forEach((cle, somme) -> moyennes.put(cle, somme / comptes.get(cle)));
        return new TimelineHabits(parties.size(), moyennes, comptes);
    }

    // Les plus récentes d'abord, lues par lots jusqu'à trouver PARTIES_MAX parties avec une timeline.
    private List<String> avecTimeline(String puuid, Instant since) {
        Criteria critere = Criteria.where("puuid").is(puuid);
        if (since != null) {
            critere = critere.and("startedAt").gte(since);
        }
        Query requete = Query.query(critere).with(Sort.by(Sort.Direction.DESC, "startedAt"));
        requete.fields().include("matchId");

        List<String> retenues = new ArrayList<>();
        List<String> lot = new ArrayList<>();
        for (Document ligne : mongo.getCollection(MatchParticipation.COLLECTION)
                .find(requete.getQueryObject()).sort(requete.getSortObject()).projection(requete.getFieldsObject())) {
            lot.add(ligne.getString("matchId"));
            if (lot.size() == LOT) {
                retenues.addAll(filtre(lot));
                lot.clear();
                if (retenues.size() >= PARTIES_MAX) {
                    break;
                }
            }
        }
        retenues.addAll(filtre(lot));
        return retenues.size() > PARTIES_MAX ? retenues.subList(0, PARTIES_MAX) : retenues;
    }

    private List<String> filtre(List<String> matchIds) {
        if (matchIds.isEmpty()) {
            return List.of();
        }
        Set<String> avec = new HashSet<>();
        for (String collection : List.of("riot_match_timeline", "riot_timeline_digest")) {
            mongo.getCollection(collection)
                    .find(new Document("_id", new Document("$in", matchIds)))
                    .projection(new Document("_id", 1))
                    .forEach(d -> avec.add(d.getString("_id")));
        }
        return matchIds.stream().filter(avec::contains).toList();
    }
}
