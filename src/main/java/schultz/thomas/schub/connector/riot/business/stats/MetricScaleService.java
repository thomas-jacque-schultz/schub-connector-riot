package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MetricReference;
import schultz.thomas.schub.connector.riot.api.dto.PlayerReferences;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.api.dto.ReferencesQuery;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.CachedRanking;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.StoredMetricScale;
import schultz.thomas.schub.connector.riot.data.repository.CachedRankingRepository;
import schultz.thomas.schub.connector.riot.data.repository.StoredMetricScaleRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

@Service
@RequiredArgsConstructor
public class MetricScaleService {

    public static final int MINIMUM_GAMES = 5;
    static final int POPULATION_MINIMUM = 10;
    static final String TABLE = "riot_player_position";
    private static final List<String> POSTES = List.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");
    private static final double BAS = 0.05;
    private static final double HAUT = 0.95;

    private final MongoTemplate mongo;
    private final StoredMetricScaleRepository store;
    private final CachedRankingRepository rankings;
    private final Clock clock;

    public StoredMetricScale current() {
        return store.findById(StoredMetricScale.CURRENT)
                .filter(scale -> scale.computedAt() != null && scale.leagues() != null)
                .orElseGet(this::refresh);
    }

    // Deux temps : la table joueur × poste est réécrite d'un bloc, puis les paliers se lisent dessus.
    public StoredMetricScale refresh() {
        mongo.indexOps(TABLE).ensureIndex(new Index("puuid", Sort.Direction.ASC));
        Aggregation table = Aggregation.newAggregation(
                Aggregation.match(faille().and("position").in(POSTES)),
                Aggregation.group("puuid", "position")
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
                context -> new Document("$addFields", new Document("puuid", "$_id.puuid")
                        .append("position", "$_id.position")),
                Aggregation.out(TABLE)).withOptions(Aggregation.newAggregationOptions().allowDiskUse(true).build());
        mongo.aggregate(table, MatchParticipation.class, Document.class);

        Map<String, Map<String, List<Joueur>>> parPalier = new HashMap<>();
        List<Document> lignes = mongo.find(Query.query(Criteria.where("games").gte(MINIMUM_GAMES)), Document.class, TABLE);
        Map<String, String> paliers = paliers(lignes.stream().map(ligne -> ligne.getString("puuid")).toList());
        for (Document ligne : lignes) {
            String palier = paliers.get(ligne.getString("puuid"));
            Joueur joueur = Joueur.of(ligne);
            if (palier != null && joueur != null) {
                parPalier.computeIfAbsent(palier, cle -> new HashMap<>())
                        .computeIfAbsent(ligne.getString("position"), cle -> new ArrayList<>())
                        .add(joueur);
            }
        }
        List<MetricReference> ligues = new ArrayList<>();
        parPalier.forEach((palier, parPoste) -> parPoste.forEach((poste, joueurs) -> {
            if (joueurs.size() >= POPULATION_MINIMUM) {
                ligues.add(new MetricReference(palier, TeamPosition.valueOf(poste), joueurs.size(), MINIMUM_GAMES,
                        bornes(joueurs)));
            }
        }));
        ligues.sort(Comparator.comparing(MetricReference::tier).thenComparing(MetricReference::position));
        StoredMetricScale scale = new StoredMetricScale(StoredMetricScale.CURRENT, clock.instant(), ligues);
        store.save(scale);
        return scale;
    }

    public List<PlayerReferences> references(List<ReferencesQuery.Player> joueurs) {
        List<MetricReference> ligues = current().leagues();
        Map<String, String> paliers = paliers(joueurs.stream().map(ReferencesQuery.Player::puuid).toList());
        List<PlayerReferences> rendus = new ArrayList<>();
        for (ReferencesQuery.Player joueur : joueurs) {
            String palier = paliers.get(joueur.puuid());
            MetricReference ligue = palier == null ? null : ligues.stream()
                    .filter(ref -> ref.tier().equals(palier) && ref.position() == joueur.position())
                    .findFirst()
                    .orElse(null);
            rendus.add(new PlayerReferences(joueur.puuid(), joueur.position(), palier, ligue, rencontres(joueur)));
        }
        return rendus;
    }

    // Les adversaires directs : l'autre joueur du même poste dans chacune de ses parties.
    private MetricReference rencontres(ReferencesQuery.Player joueur) {
        Criteria siennes = faille().and("puuid").is(joueur.puuid()).and("position").is(joueur.position().name());
        if (joueur.since() != null) {
            siennes = siennes.and("startedAt").gte(joueur.since());
        }
        Query parties = Query.query(siennes);
        parties.fields().include("matchId");
        List<String> matchIds = mongo.find(parties, Document.class, "riot_participation").stream()
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
        mongo.find(face, Document.class, "riot_participation")
                .forEach(document -> adversaires.add(document.getString("puuid")));

        List<Joueur> population = mongo.find(Query.query(Criteria.where("puuid").in(adversaires)
                        .and("position").is(joueur.position().name())
                        .and("games").gte(MINIMUM_GAMES)), Document.class, TABLE).stream()
                .map(Joueur::of)
                .filter(Objects::nonNull)
                .toList();
        if (population.size() < POPULATION_MINIMUM) {
            return null;
        }
        return new MetricReference(null, joueur.position(), population.size(), MINIMUM_GAMES, bornes(population));
    }

    private Map<String, String> paliers(List<String> puuids) {
        Map<String, String> paliers = new HashMap<>();
        for (CachedRanking ranking : rankings.findAllById(puuids)) {
            palier(ranking.standings()).ifPresent(palier -> paliers.put(ranking.puuid(), palier));
        }
        return paliers;
    }

    static Optional<String> palier(List<RankedStanding> standings) {
        if (standings == null) {
            return Optional.empty();
        }
        return file(standings, QueueKind.RANKED_SOLO).or(() -> file(standings, QueueKind.RANKED_FLEX));
    }

    private static Optional<String> file(List<RankedStanding> standings, QueueKind queue) {
        return standings.stream()
                .filter(standing -> standing.queue() == queue && standing.tier() != null)
                .map(RankedStanding::tier)
                .findFirst();
    }

    private static Criteria faille() {
        return Criteria.where("queueId").in(QueueKind.RIFT_QUEUE_IDS).and("afk").is(false);
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
