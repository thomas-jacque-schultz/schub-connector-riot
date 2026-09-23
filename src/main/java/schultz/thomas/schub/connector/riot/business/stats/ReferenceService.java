package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.ChampionReferenceGrid;
import schultz.thomas.schub.connector.riot.api.dto.ReferenceGrid;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.StoredChampionReference;
import schultz.thomas.schub.connector.riot.data.model.StoredReference;
import schultz.thomas.schub.connector.riot.data.repository.StoredChampionReferenceRepository;
import schultz.thomas.schub.connector.riot.data.repository.StoredReferenceRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceService {

    public static final String GAME = "GAME";
    public static final String MEAN = "MEAN";
    public static final List<String> PALIERS = List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD",
            "DIAMOND", "MASTER_PLUS");
    static final List<String> POSTES = List.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");
    static final List<Integer> FILES_CLASSEES = List.of(420, 440);
    static final List<Double> PERCENTILES = percentiles();
    static final int PATCHS = 2;
    static final int PARTIES_PAR_JOUEUR = 10;
    static final int MINIMUM_PARTIES = 200;
    static final int MINIMUM_JOUEURS = 30;
    static final int PARTIES_PAR_CHAMPION = 5;
    static final long DUREE_MINIMUM = 600;
    private static final Duration RECUL_PATCHS = Duration.ofDays(120);
    private static final int RETAMPONNAGE_MAX = 20_000;

    private final MongoTemplate mongo;
    private final StoredReferenceRepository store;
    private final StoredChampionReferenceRepository champions;
    private final ParticipationProjector projector;
    private final RiotProperties properties;
    private final Clock clock;

    public Optional<StoredReference> latest(String scope, String position) {
        return store.findFirstByScopeAndPositionOrderByComputedAtDesc(scope, position);
    }

    public Optional<StoredReference> forPatch(String scope, String position, String patch) {
        return store.findByScopeAndPositionAndPatchesContainingOrderByComputedAtDesc(scope, position, patch).stream()
                .findFirst()
                .or(() -> latest(scope, position));
    }

    public ReferenceGrid toGrid(StoredReference reference, String tier) {
        String groupe = groupe(tier);
        Map<String, ReferenceGrid.Metric> metriques = new LinkedHashMap<>();
        reference.metrics().forEach((cle, grille) -> {
            Map<String, ReferenceGrid.Tier> paliers = new LinkedHashMap<>();
            grille.tiers().forEach((palier, valeurs) -> {
                if (groupe == null || groupe.equals(palier)) {
                    paliers.put(palier, new ReferenceGrid.Tier(valeurs.count(), valeurs.values()));
                }
            });
            metriques.put(cle, new ReferenceGrid.Metric(polarite(cle), paliers, grille.ladder(), grille.missingTiers()));
        });
        return new ReferenceGrid(reference.patches(), reference.scope(), TeamPosition.valueOf(reference.position()),
                reference.computedAt(), reference.distribution(), reference.percentiles(), niveaux(), metriques);
    }

    public Optional<ChampionReferenceGrid> championGrid(int championId, String tier) {
        String groupe = groupeChampion(tier);
        return champions.findFirstByChampionIdOrderByComputedAtDesc(championId).map(reference -> {
            Map<String, ChampionReferenceGrid.Metric> metriques = new LinkedHashMap<>();
            reference.metrics().forEach((cle, parGroupe) -> {
                StoredReference.TierGrid grille = groupe == null ? null : parGroupe.get(groupe);
                if (grille != null) {
                    metriques.put(cle, new ChampionReferenceGrid.Metric(polarite(cle), grille.count(), grille.values()));
                }
            });
            return new ChampionReferenceGrid(championId, groupe, reference.patches(), reference.computedAt(),
                    reference.percentiles(), metriques);
        });
    }

    static String groupeChampion(String tier) {
        String groupe = groupe(tier);
        if (groupe == null) {
            return null;
        }
        return switch (groupe) {
            case "IRON", "BRONZE" -> "IRON_BRONZE";
            case "SILVER", "GOLD" -> "SILVER_GOLD";
            case "PLATINUM", "EMERALD" -> "PLATINUM_EMERALD";
            default -> groupe;
        };
    }

    // Maître, GM et Challenger ne forment qu'un palier tant que la population ne permet pas de les séparer.
    static String groupe(String tier) {
        if (tier == null || tier.isBlank()) {
            return null;
        }
        return List.of("MASTER", "GRANDMASTER", "CHALLENGER").contains(tier) ? "MASTER_PLUS" : tier;
    }

    List<ReferenceGrid.Level> niveaux() {
        Map<String, Double> parts = properties.getLadder().getShares();
        double total = PALIERS.stream().mapToDouble(palier -> parts.getOrDefault(palier, 0.0)).sum();
        List<ReferenceGrid.Level> niveaux = new ArrayList<>();
        double cumul = 0;
        for (String palier : PALIERS) {
            niveaux.add(new ReferenceGrid.Level(palier, cumul / total));
            cumul += parts.getOrDefault(palier, 0.0);
        }
        return niveaux;
    }

    private static String polarite(String cle) {
        return ReferenceMetric.CATALOGUE.stream()
                .filter(metrique -> metrique.key().equals(cle))
                .map(metrique -> metrique.polarity().name())
                .findFirst()
                .orElse(ReferenceMetric.Polarity.NEUTRAL.name());
    }

    public boolean missing() {
        return store.count() == 0;
    }

    public List<StoredReference> refresh() {
        mongo.indexOps(MatchParticipation.COLLECTION).ensureIndex(new Index()
                .on("patch", Sort.Direction.ASC).on("startedAt", Sort.Direction.ASC).named(PatchCalendar.INDEX));
        List<String> patchs = derniersPatchs();
        if (patchs.isEmpty()) {
            return List.of();
        }
        retamponne(patchs);
        Instant maintenant = clock.instant();
        List<StoredReference> rendus = new ArrayList<>();
        for (String scope : List.of(GAME, MEAN)) {
            Map<String, Map<String, Map<String, StoredReference.TierGrid>>> parPoste = grilles(scope, patchs);
            for (String poste : POSTES) {
                Map<String, Map<String, StoredReference.TierGrid>> parMetrique = parPoste.getOrDefault(poste, Map.of());
                Map<String, StoredReference.Grid> metriques = new LinkedHashMap<>();
                for (ReferenceMetric metrique : ReferenceMetric.CATALOGUE) {
                    if (scope.equals(GAME) && !metrique.perGame()) {
                        continue;
                    }
                    metriques.put(metrique.key(), grille(scope, parMetrique.getOrDefault(metrique.key(), Map.of())));
                }
                rendus.add(new StoredReference(String.join("/", scope, poste, String.join("+", patchs)), patchs,
                        scope, poste, maintenant, properties.getLadder().getLabel(), PERCENTILES, metriques));
            }
        }
        store.saveAll(rendus);
        List<StoredChampionReference> parChampion = champions(patchs, maintenant);
        champions.saveAll(parChampion);
        log.info("Référentiels calculés sur les patchs {} : {} champions assez joués.", patchs, parChampion.size());
        return rendus;
    }

    private List<StoredChampionReference> champions(List<String> patchs, Instant maintenant) {
        Map<Integer, Map<String, Map<String, StoredReference.TierGrid>>> parChampion = new HashMap<>();
        List<Document> pipeline = ReferencePipeline.parChampion(patchs, ReferenceMetric.CATALOGUE, PARTIES_PAR_CHAMPION);
        mongo.getCollection(MatchParticipation.COLLECTION).aggregate(pipeline).allowDiskUse(true).forEach(ligne -> {
            Document id = ligne.get("_id", Document.class);
            int championId = nombre(id.get("championId")).intValue();
            String groupe = id.getString("tier");
            for (ReferenceMetric metrique : ReferenceMetric.CATALOGUE) {
                long effectif = nombre(ligne.get("c_" + metrique.key())).longValue();
                List<?> valeurs = ligne.getList("q_" + metrique.key(), Object.class);
                if (effectif < MINIMUM_JOUEURS || valeurs == null) {
                    continue;
                }
                parChampion.computeIfAbsent(championId, cle -> new HashMap<>())
                        .computeIfAbsent(metrique.key(), cle -> new HashMap<>())
                        .put(groupe, new StoredReference.TierGrid(effectif, monotone(valeurs)));
            }
        });
        List<StoredChampionReference> rendus = new ArrayList<>();
        parChampion.forEach((championId, metriques) -> rendus.add(new StoredChampionReference(
                championId + "/" + String.join("+", patchs), championId, patchs, maintenant, PERCENTILES, metriques)));
        return rendus;
    }

    private StoredReference.Grid grille(String scope, Map<String, StoredReference.TierGrid> parPalier) {
        long minimum = scope.equals(GAME) ? MINIMUM_PARTIES : MINIMUM_JOUEURS;
        List<String> manquants = PALIERS.stream()
                .filter(palier -> !parPalier.containsKey(palier) || parPalier.get(palier).count() < minimum)
                .toList();
        List<Double> ladder = null;
        if (manquants.isEmpty()) {
            Map<String, List<Double>> valeurs = new HashMap<>();
            parPalier.forEach((palier, grille) -> valeurs.put(palier, grille.values()));
            ladder = LadderMixture.mix(PERCENTILES, valeurs, properties.getLadder().getShares());
        }
        return new StoredReference.Grid(parPalier, ladder, manquants);
    }

    // Le plus récent d'abord ; « 16.18 » passe devant « 16.9 ».
    List<String> derniersPatchs() {
        Query query = Query.query(Criteria.where("queueId").in(FILES_CLASSEES)
                .and("startedAt").gte(Date.from(clock.instant().minus(RECUL_PATCHS))));
        return mongo.findDistinct(query, "patch", MatchParticipation.COLLECTION, String.class).stream()
                .filter(patch -> patch != null && patch.matches("\\d+\\.\\d+"))
                .sorted(ReferenceService.parVersion().reversed())
                .limit(PATCHS)
                .toList();
    }

    static Comparator<String> parVersion() {
        return Comparator.<String>comparingInt(patch -> Integer.parseInt(patch.split("\\.")[0]))
                .thenComparingInt(patch -> Integer.parseInt(patch.split("\\.")[1]));
    }

    // Un rang relevé après la projection d'une partie (page de classement lue plus tard) : on reprojette.
    private void retamponne(List<String> patchs) {
        Query sansRang = Query.query(Criteria.where("patch").in(patchs).and("queueId").in(FILES_CLASSEES)
                .and("rank").is(null)).limit(RETAMPONNAGE_MAX);
        sansRang.fields().include("matchId");
        List<String> matchIds = mongo.find(sansRang, Document.class, MatchParticipation.COLLECTION).stream()
                .map(ligne -> ligne.getString("matchId"))
                .distinct()
                .toList();
        matchIds.forEach(projector::reproject);
    }

    private Map<String, Map<String, Map<String, StoredReference.TierGrid>>> grilles(String scope, List<String> patchs) {
        List<ReferenceMetric> metriques = ReferenceMetric.CATALOGUE.stream()
                .filter(metrique -> scope.equals(MEAN) || metrique.perGame())
                .toList();
        List<Document> pipeline = scope.equals(GAME)
                ? ReferencePipeline.parPartie(patchs, metriques)
                : ReferencePipeline.parMoyenne(patchs, metriques);
        Map<String, Map<String, Map<String, StoredReference.TierGrid>>> parPoste = new HashMap<>();
        mongo.getCollection(MatchParticipation.COLLECTION).aggregate(pipeline).allowDiskUse(true).forEach(ligne -> {
            Document id = ligne.get("_id", Document.class);
            String poste = id.getString("position");
            String palier = id.getString("tier");
            for (ReferenceMetric metrique : metriques) {
                long effectif = nombre(ligne.get("c_" + metrique.key())).longValue();
                List<?> valeurs = ligne.getList("q_" + metrique.key(), Object.class);
                if (effectif == 0 || valeurs == null) {
                    continue;
                }
                parPoste.computeIfAbsent(poste, cle -> new HashMap<>())
                        .computeIfAbsent(metrique.key(), cle -> new HashMap<>())
                        .put(palier, new StoredReference.TierGrid(effectif, monotone(valeurs)));
            }
        });
        return parPoste;
    }

    // $percentile approché n'est pas garanti croissant : on redresse, la répartition doit l'être.
    private static List<Double> monotone(List<?> valeurs) {
        List<Double> rendues = new ArrayList<>();
        double precedente = Double.NEGATIVE_INFINITY;
        for (Object valeur : valeurs) {
            precedente = Math.max(precedente, nombre(valeur).doubleValue());
            rendues.add(precedente);
        }
        return rendues;
    }

    private static Number nombre(Object valeur) {
        return valeur instanceof Number n ? n : 0;
    }

    private static List<Double> percentiles() {
        List<Double> p = new ArrayList<>();
        p.add(0.0);
        IntStream.rangeClosed(1, 99).forEach(i -> p.add(i / 100.0));
        p.add(0.995);
        p.add(0.999);
        p.add(1.0);
        return List.copyOf(p);
    }
}
