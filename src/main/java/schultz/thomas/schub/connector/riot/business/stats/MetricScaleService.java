package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MetricReference;
import schultz.thomas.schub.connector.riot.api.dto.PlayerReferences;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.ReferencesQuery;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;

// Les adversaires directs d'un joueur : une population de quelques dizaines, calculée à la demande, bornée p5–p95.
@Service
@RequiredArgsConstructor
public class MetricScaleService {

    public static final int MINIMUM_GAMES = 5;
    static final int POPULATION_MINIMUM = 10;
    private static final double BAS = 0.05;
    private static final double HAUT = 0.95;

    private final MongoTemplate mongo;
    private final RankHistory history;

    public List<PlayerReferences> references(List<ReferencesQuery.Player> joueurs) {
        Map<String, String> paliers = history.latestTiers(joueurs.stream().map(ReferencesQuery.Player::puuid).toList());
        List<PlayerReferences> rendus = new ArrayList<>();
        for (ReferencesQuery.Player joueur : joueurs) {
            rendus.add(new PlayerReferences(joueur.puuid(), joueur.position(), paliers.get(joueur.puuid()),
                    rencontres(joueur)));
        }
        return rendus;
    }

    private MetricReference rencontres(ReferencesQuery.Player joueur) {
        Criteria siennes = faille(joueur).and("puuid").is(joueur.puuid());
        Query parties = Query.query(siennes);
        parties.fields().include("matchId");
        List<String> matchIds = mongo.find(parties, Document.class, MatchParticipation.COLLECTION).stream()
                .map(document -> document.getString("matchId"))
                .toList();
        if (matchIds.isEmpty()) {
            return null;
        }
        Query face = Query.query(Criteria.where("matchId").in(matchIds)
                .and("position").is(joueur.position().name())
                .and("puuid").ne(joueur.puuid()));
        face.fields().include("puuid");
        Set<String> adversaires = new HashSet<>();
        mongo.find(face, Document.class, MatchParticipation.COLLECTION)
                .forEach(document -> adversaires.add(document.getString("puuid")));

        Aggregation sommes = Aggregation.newAggregation(
                Aggregation.match(faille(joueur).and("puuid").in(adversaires)),
                Aggregation.group("puuid")
                        .count().as("games")
                        .sum(context -> new Document("$cond", List.of("$win", 1, 0))).as("wins")
                        .sum("kills").as("kills")
                        .sum("deaths").as("deaths")
                        .sum("assists").as("assists")
                        .sum("minionsKilled").as("minionsKilled")
                        .sum("goldEarned").as("goldEarned")
                        .sum("damageToChampions").as("damageToChampions")
                        .sum("damageTaken").as("damageTaken")
                        .sum("visionScore").as("visionScore")
                        .sum("teamKills").as("teamKills")
                        .sum("teamDeaths").as("teamDeaths")
                        .sum("durationSeconds").as("secondsPlayed"),
                Aggregation.match(Criteria.where("games").gte(MINIMUM_GAMES)));
        List<Joueur> population = mongo.aggregate(sommes, MatchParticipation.class, Document.class)
                .getMappedResults().stream()
                .map(Joueur::of)
                .filter(Objects::nonNull)
                .toList();
        if (population.size() < POPULATION_MINIMUM) {
            return null;
        }
        return new MetricReference(null, joueur.position(), population.size(), MINIMUM_GAMES, bornes(population));
    }

    private static Criteria faille(ReferencesQuery.Player joueur) {
        Criteria criteria = Criteria.where("queueId").in(QueueKind.RIFT_QUEUE_IDS).and("afk").is(false)
                .and("position").is(joueur.position().name());
        return joueur.since() == null ? criteria : criteria.and("startedAt").gte(Date.from(joueur.since()));
    }

    private static Map<String, MetricReference.Bound> bornes(List<Joueur> joueurs) {
        Map<String, MetricReference.Bound> bornes = new LinkedHashMap<>();
        bornes.put("winRate", borne(joueurs, Joueur::winRate));
        bornes.put("kda", borne(joueurs, Joueur::kda));
        bornes.put("csPerMinute", borne(joueurs, Joueur::csPerMinute));
        bornes.put("goldPerMinute", borne(joueurs, Joueur::goldPerMinute));
        bornes.put("damagePerMinute", borne(joueurs, Joueur::damagePerMinute));
        bornes.put("damageTakenPerMinute", borne(joueurs, Joueur::damageTakenPerMinute));
        bornes.put("visionPerMinute", borne(joueurs, Joueur::visionPerMinute));
        bornes.put("killParticipation", borne(joueurs, Joueur::killParticipation));
        bornes.put("deathShare", borne(joueurs, Joueur::deathShare));
        return bornes;
    }

    private static MetricReference.Bound borne(List<Joueur> joueurs, ToDoubleFunction<Joueur> valeur) {
        double[] valeurs = joueurs.stream().mapToDouble(valeur).sorted().toArray();
        return new MetricReference.Bound(quantile(valeurs, BAS), quantile(valeurs, HAUT));
    }

    static double quantile(double[] tries, double q) {
        if (tries.length == 1) {
            return tries[0];
        }
        double position = q * (tries.length - 1);
        int bas = (int) Math.floor(position);
        int haut = Math.min(bas + 1, tries.length - 1);
        return tries[bas] + (position - bas) * (tries[haut] - tries[bas]);
    }

    private static double nombre(Document row, String champ) {
        Object valeur = row.get(champ);
        return valeur instanceof Number n ? n.doubleValue() : 0.0;
    }

    record Joueur(double winRate, double kda, double csPerMinute, double goldPerMinute,
                  double damagePerMinute, double damageTakenPerMinute, double visionPerMinute,
                  double killParticipation, double deathShare) {

        static Joueur of(Document row) {
            double minutes = nombre(row, "secondsPlayed") / 60.0;
            if (minutes <= 0) {
                return null;
            }
            double morts = nombre(row, "deaths");
            double positif = nombre(row, "kills") + nombre(row, "assists");
            double killsEquipe = nombre(row, "teamKills");
            double mortsEquipe = nombre(row, "teamDeaths");
            return new Joueur(
                    nombre(row, "wins") / nombre(row, "games"),
                    morts == 0 ? positif : positif / morts,
                    nombre(row, "minionsKilled") / minutes,
                    nombre(row, "goldEarned") / minutes,
                    nombre(row, "damageToChampions") / minutes,
                    nombre(row, "damageTaken") / minutes,
                    nombre(row, "visionScore") / minutes,
                    killsEquipe == 0 ? 0 : positif / killsEquipe,
                    mortsEquipe == 0 ? 0 : morts / mortsEquipe);
        }
    }
}
