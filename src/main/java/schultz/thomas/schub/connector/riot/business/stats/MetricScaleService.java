package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MetricScale;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.StoredMetricScale;
import schultz.thomas.schub.connector.riot.data.repository.StoredMetricScaleRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Service
@RequiredArgsConstructor
public class MetricScaleService {

    public static final int MINIMUM_GAMES = 10;
    private static final int PATCHES_RENDUS = 4;
    private static final double BAS = 0.05;
    private static final double HAUT = 0.95;

    private final MongoTemplate mongo;
    private final StoredMetricScaleRepository store;
    private final Clock clock;

    public MetricScale current() {
        return store.findById(StoredMetricScale.CURRENT)
                .map(StoredMetricScale::scale)
                .orElseGet(this::refresh);
    }

    public MetricScale refresh() {
        MetricScale scale = compute();
        store.save(new StoredMetricScale(StoredMetricScale.CURRENT, scale));
        return scale;
    }

    private MetricScale compute() {
        Criteria faille = Criteria.where("queueId").in(QueueKind.RIFT_QUEUE_IDS).and("afk").is(false);
        Aggregation parJoueur = Aggregation.newAggregation(
                Aggregation.match(faille),
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
                        .sum("durationSeconds").as("secondsPlayed"),
                Aggregation.match(Criteria.where("games").gte(MINIMUM_GAMES)));

        List<Joueur> joueurs = new ArrayList<>();
        for (Document row : mongo.aggregate(parJoueur, MatchParticipation.class, Document.class)) {
            double minutes = nombre(row, "secondsPlayed") / 60.0;
            if (minutes <= 0) {
                continue;
            }
            double morts = nombre(row, "deaths");
            double positif = nombre(row, "kills") + nombre(row, "assists");
            joueurs.add(new Joueur(
                    nombre(row, "wins") / nombre(row, "games"),
                    morts == 0 ? positif : positif / morts,
                    nombre(row, "minionsKilled") / minutes,
                    nombre(row, "goldEarned") / minutes,
                    nombre(row, "damageToChampions") / minutes,
                    nombre(row, "damageTaken") / minutes,
                    nombre(row, "visionScore") / minutes));
        }

        Map<String, MetricScale.Bound> bounds = new LinkedHashMap<>();
        if (!joueurs.isEmpty()) {
            bounds.put("winRate", borne(joueurs, Joueur::winRate));
            bounds.put("kda", borne(joueurs, Joueur::kda));
            bounds.put("csPerMinute", borne(joueurs, Joueur::csPerMinute));
            bounds.put("goldPerMinute", borne(joueurs, Joueur::goldPerMinute));
            bounds.put("damagePerMinute", borne(joueurs, Joueur::damagePerMinute));
            bounds.put("damageTakenPerMinute", borne(joueurs, Joueur::damageTakenPerMinute));
            bounds.put("visionPerMinute", borne(joueurs, Joueur::visionPerMinute));
        }
        return new MetricScale(clock.instant(), joueurs.size(), MINIMUM_GAMES, patchesRecents(faille),
                bounds);
    }

    private List<String> patchesRecents(Criteria faille) {
        List<String> patches = mongo.findDistinct(Query.query(faille), "patch",
                MatchParticipation.class, String.class);
        return patches.stream()
                .filter(patch -> patch != null && patch.matches("\\d+\\.\\d+"))
                .sorted(PAR_VERSION.reversed())
                .limit(PATCHES_RENDUS)
                .toList();
    }

    // « 16.9 » précède « 16.13 » : l'ordre alphabétique les inverserait.
    static final Comparator<String> PAR_VERSION = Comparator
            .comparingInt((String patch) -> Integer.parseInt(patch.split("\\.")[0]))
            .thenComparingInt(patch -> Integer.parseInt(patch.split("\\.")[1]));

    private static MetricScale.Bound borne(List<Joueur> joueurs, ToDoubleFunction<Joueur> valeur) {
        double[] valeurs = joueurs.stream().mapToDouble(valeur).sorted().toArray();
        return new MetricScale.Bound(quantile(valeurs, BAS), quantile(valeurs, HAUT));
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

    private record Joueur(double winRate, double kda, double csPerMinute, double goldPerMinute,
                          double damagePerMinute, double damageTakenPerMinute, double visionPerMinute) {
    }

}
