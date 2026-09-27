package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.connector.riot.business.services.ItemCatalogService;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedTimelineDigestRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedTimelineRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les capteurs de partie du moteur de constats, retenus par les R&D Schub#14, #19 et #26 (docs/rd/ de Schub).
 * Build : sur le détail. Comportement : sur la timeline complète (parties d'équipe) ou le digest (échantillon),
 * dont les positions ne sont connues qu'à la minute ; une mort n'est dite isolée que si c'est sûr.
 */
@Service
@RequiredArgsConstructor
public class MatchSignalsService {

    static final double ISOLE = 2000;
    static final double VITESSE_MAX = 450;
    static final long CHAINE_MS = 30_000;
    static final long COMBAT_ECART_MS = 20_000;
    static final double COMBAT_RAYON = 3000;
    static final int COMBAT_MINIMUM = 3;
    static final double OBJECTIF_GROUPE = 3000;
    static final long ECHANGE_MS = 60_000;
    static final long GANK_AVANT_MS = 15 * 60_000;
    static final double ARMURE_COMBATTANT = 60;
    static final Set<String> MONSTRES = Set.of("DRAGON", "BARON_NASHOR", "RIFTHERALD", "HORDE");
    static final Set<String> GROS_OBJECTIFS = Set.of("DRAGON", "BARON_NASHOR");

    private final CachedMatchRepository matches;
    private final CachedTimelineRepository timelines;
    private final CachedTimelineDigestRepository digests;
    private final ItemCatalogService items;

    public Map<String, Map<String, Double>> of(String matchId) {
        Optional<CachedMatch> partie = matches.findById(matchId);
        if (partie.isEmpty()) {
            return Map.of();
        }
        Document info = partie.get().raw().get("info", Document.class);
        List<Document> joueurs = info == null ? List.of() : info.getList("participants", Document.class);
        Map<String, Map<String, Double>> signaux = new LinkedHashMap<>();
        joueurs.forEach(j -> signaux.put(j.getString("puuid"), new LinkedHashMap<>()));

        items.current().ifPresent(catalogue -> build(joueurs, catalogue, signaux));

        Optional<Document> complete = timelines.findById(matchId).map(t -> t.raw().get("info", Document.class));
        Optional<Document> timeline = complete.or(() -> digests.findById(matchId).map(d -> d.raw().get("info", Document.class)));
        timeline.ifPresent(t -> comportement(joueurs, t, complete.isPresent(), signaux));
        return signaux;
    }

    // --- Build (R&D #19) ----------------------------------------------------------------------------------

    static void build(List<Document> joueurs, ItemCatalogService.ItemCatalog catalogue,
                      Map<String, Map<String, Double>> signaux) {
        Map<Integer, Integer> porteursParCamp = new HashMap<>();
        for (Document j : joueurs) {
            long crit = objets(j).stream().filter(catalogue.critical()::contains).count();
            if (crit >= 2) {
                porteursParCamp.merge(j.getInteger("teamId", 0), 1, Integer::sum);
            }
        }
        for (Document j : joueurs) {
            Map<String, Double> s = signaux.get(j.getString("puuid"));
            List<Integer> build = objets(j);
            double armure = build.stream().mapToDouble(i -> catalogue.armor().getOrDefault(i, 0.0)).sum();
            double rm = build.stream().mapToDouble(i -> catalogue.magicResist().getOrDefault(i, 0.0)).sum();
            int camp = j.getInteger("teamId", 0);
            s.put("enemyCritCarries", (double) porteursParCamp.getOrDefault(camp == 100 ? 200 : 100, 0));
            String poste = j.getString("teamPosition");
            s.put("frontlineArmored", ("TOP".equals(poste) || "JUNGLE".equals(poste)) && armure >= ARMURE_COMBATTANT ? 1.0 : 0.0);
            s.put("hasAntiCrit", build.stream().anyMatch(ItemCatalogService.ANTI_CRITIQUE::contains) ? 1.0 : 0.0);
            double magique = nombre(j.get("magicDamageTaken"));
            double physique = nombre(j.get("physicalDamageTaken"));
            if (magique + physique > 0) {
                s.put("magicDamageShare", magique / (magique + physique));
            }
            s.put("armorOverMr", armure > rm ? 1.0 : 0.0);
            s.put("resistancesTotal", armure + rm);
        }
    }

    // Une liste et non un ensemble : deux items identiques comptent deux fois leurs résistances.
    private static List<Integer> objets(Document joueur) {
        List<Integer> build = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            Object id = joueur.get("item" + i);
            if (id instanceof Number n && n.intValue() > 0) {
                build.add(n.intValue());
            }
        }
        return build;
    }

    // --- Comportement (R&D #14 et #26) -------------------------------------------------------------------

    record Kill(int killer, int victim, long t, double x, double y, List<Integer> assists, List<Document> recus) {
    }

    static void comportement(List<Document> joueurs, Document timeline, boolean complete,
                             Map<String, Map<String, Double>> signaux) {
        List<Document> frames = timeline.getList("frames", Document.class);
        Map<Integer, String> puuidDe = new HashMap<>();
        for (Document p : timeline.getList("participants", Document.class)) {
            puuidDe.put(p.getInteger("participantId"), p.getString("puuid"));
        }
        Map<String, Document> parPuuid = new HashMap<>();
        joueurs.forEach(j -> parPuuid.put(j.getString("puuid"), j));
        Map<Integer, Integer> camp = new HashMap<>();
        Map<Integer, String> poste = new HashMap<>();
        puuidDe.forEach((pid, puuid) -> {
            Document j = parPuuid.get(puuid);
            if (j != null) {
                camp.put(pid, j.getInteger("teamId", 0));
                poste.put(pid, j.getString("teamPosition"));
            }
        });
        if (camp.size() != 10) {
            return;
        }

        List<Kill> kills = new ArrayList<>();
        List<Document> autres = new ArrayList<>();
        for (Document f : frames) {
            for (Document e : f.getList("events", Document.class, List.of())) {
                String type = e.getString("type");
                if ("CHAMPION_KILL".equals(type) && camp.containsKey(e.getInteger("victimId"))) {
                    Document pos = e.get("position", Document.class);
                    kills.add(new Kill(e.getInteger("killerId", 0), e.getInteger("victimId"), temps(e),
                            nombre(pos.get("x")), nombre(pos.get("y")),
                            e.getList("assistingParticipantIds", Integer.class, List.of()),
                            e.getList("victimDamageReceived", Document.class, List.of())));
                } else if ("ELITE_MONSTER_KILL".equals(type) || "BUILDING_KILL".equals(type)
                        || "TURRET_PLATE_DESTROYED".equals(type)) {
                    autres.add(e);
                }
            }
        }

        Map<Integer, Double> isolees = new HashMap<>();
        Map<Integer, Double> gank = new HashMap<>();
        Map<Integer, Double> echanges = new HashMap<>();
        for (Kill k : kills) {
            if (mortIsoleeSure(k, frames, camp)) {
                isolees.merge(k.victim(), 1.0, Double::sum);
            }
            if (complete) {
                cause(k, camp, poste, gank, echanges);
            }
        }

        Map<Integer, Integer> mortsCamp = new HashMap<>();
        Map<Integer, Integer> enchainees = new HashMap<>();
        for (int i = 0; i < kills.size(); i++) {
            Kill k = kills.get(i);
            int c = camp.get(k.victim());
            mortsCamp.merge(c, 1, Integer::sum);
            for (int j = i + 1; j < kills.size() && kills.get(j).t() - k.t() <= CHAINE_MS; j++) {
                if (camp.get(kills.get(j).victim()) == c) {
                    enchainees.merge(c, 1, Integer::sum);
                    break;
                }
            }
        }

        Map<Integer, Double> premiersMorts = new HashMap<>();
        Map<Integer, Double> inferiorite = new HashMap<>();
        for (List<Kill> combat : combats(kills)) {
            premiersMorts.merge(combat.get(0).victim(), 1.0, Double::sum);
            if (complete) {
                Map<Integer, Set<Integer>> impliques = new HashMap<>();
                for (Kill k : combat.subList(0, Math.min(2, combat.size()))) {
                    impliques.computeIfAbsent(camp.getOrDefault(k.killer(), 0), x -> new HashSet<>()).add(k.killer());
                    impliques.computeIfAbsent(camp.get(k.victim()), x -> new HashSet<>()).add(k.victim());
                    k.assists().forEach(a -> impliques.computeIfAbsent(camp.getOrDefault(a, 0), x -> new HashSet<>()).add(a));
                }
                for (int c : List.of(100, 200)) {
                    int nous = impliques.getOrDefault(c, Set.of()).size();
                    int eux = impliques.getOrDefault(c == 100 ? 200 : 100, Set.of()).size();
                    if (nous < eux) {
                        inferiorite.merge(c, 1.0, Double::sum);
                    }
                }
            }
        }

        Map<Integer, Double> cedes = new HashMap<>();
        Map<Integer, int[]> groupes = new HashMap<>();
        for (Document e : autres) {
            if (!"ELITE_MONSTER_KILL".equals(e.getString("type")) || !MONSTRES.contains(e.getString("monsterType"))) {
                continue;
            }
            int preneur = e.getInteger("killerTeamId", 0);
            long t = temps(e);
            for (int c : List.of(100, 200)) {
                if (c != preneur && !echange(autres, e, c, t)) {
                    cedes.merge(c, 1.0, Double::sum);
                }
            }
            if (GROS_OBJECTIFS.contains(e.getString("monsterType"))) {
                Document image = derniereImage(frames, t);
                Document pos = e.get("position", Document.class);
                if (image != null && pos != null) {
                    for (int c : List.of(100, 200)) {
                        long proches = camp.entrySet().stream().filter(x -> x.getValue() == c)
                                .filter(x -> distance(positionA(image, x.getKey()), pos) <= OBJECTIF_GROUPE).count();
                        int[] compte = groupes.computeIfAbsent(c, x -> new int[2]);
                        compte[0] += proches >= 3 ? 1 : 0;
                        compte[1]++;
                    }
                }
            }
        }

        camp.forEach((pid, c) -> {
            Map<String, Double> s = signaux.get(puuidDe.get(pid));
            if (s == null) {
                return;
            }
            s.put("isolatedDeaths", isolees.getOrDefault(pid, 0.0));
            s.put("firstDeathsInFights", premiersMorts.getOrDefault(pid, 0.0));
            s.put("objectivesCededWithoutTrade", cedes.getOrDefault(c, 0.0));
            if (mortsCamp.getOrDefault(c, 0) >= 5) {
                s.put("chainDeathShare", enchainees.getOrDefault(c, 0) / (double) mortsCamp.get(c));
            }
            int[] g = groupes.get(c);
            if (g != null && g[1] > 0) {
                s.put("groupedAtObjectivesShare", g[0] / (double) g[1]);
            }
            if (complete) {
                s.put("gankDeaths", gank.getOrDefault(pid, 0.0));
                s.put("lostTradeDeaths", echanges.getOrDefault(pid, 0.0));
                s.put("outnumberedFights", inferiorite.getOrDefault(c, 0.0));
            }
        });
    }

    // La victime est à la position de l'élimination ; un allié n'est connu qu'à la minute. On ne conclut que si
    // l'allié le plus proche est loin même au pire de ce qu'il a pu parcourir depuis l'image la plus proche.
    static boolean mortIsoleeSure(Kill k, List<Document> frames, Map<Integer, Integer> camp) {
        int c = camp.get(k.victim());
        double plusProche = Double.MAX_VALUE;
        for (Map.Entry<Integer, Integer> allie : camp.entrySet()) {
            if (allie.getValue() != c || allie.getKey() == k.victim()) {
                continue;
            }
            double[] borne = distanceBornee(frames, allie.getKey(), k.t(), k.x(), k.y());
            if (borne == null) {
                return false;
            }
            plusProche = Math.min(plusProche, borne[0]);
        }
        return plusProche > ISOLE;
    }

    private static double[] distanceBornee(List<Document> frames, int pid, long t, double x, double y) {
        int i = (int) Math.min(t / 60_000, frames.size() - 1);
        Document avant = positionA(frames.get(i), pid);
        if (avant == null) {
            return null;
        }
        long t0 = temps(frames.get(i));
        Document apres = i + 1 < frames.size() ? positionA(frames.get(i + 1), pid) : null;
        long t1 = apres == null ? t0 : temps(frames.get(i + 1));
        double r = t1 == t0 ? 0 : (t - t0) / (double) (t1 - t0);
        double px = nombre(avant.get("x")) + (apres == null ? 0 : r * (nombre(apres.get("x")) - nombre(avant.get("x"))));
        double py = nombre(avant.get("y")) + (apres == null ? 0 : r * (nombre(apres.get("y")) - nombre(avant.get("y"))));
        double incertitude = VITESSE_MAX * Math.min(t - t0, apres == null ? t - t0 : t1 - t) / 1000.0;
        double d = Math.hypot(px - x, py - y);
        return new double[]{d - incertitude, d + incertitude};
    }

    // Cause d'une mort, par les dégâts reçus : un gank (jungler et un autre, avant 15 min), ou un échange
    // perdu face à son adversaire direct (un seul ennemi, du même poste).
    private static void cause(Kill k, Map<Integer, Integer> camp, Map<Integer, String> poste,
                              Map<Integer, Double> gank, Map<Integer, Double> echanges) {
        Set<Integer> ennemis = new HashSet<>();
        for (Document d : k.recus()) {
            Integer pid = d.getInteger("participantId");
            if ("OTHER".equals(d.getString("type")) && pid != null && camp.containsKey(pid)
                    && !camp.get(pid).equals(camp.get(k.victim()))) {
                ennemis.add(pid);
            }
        }
        if (ennemis.size() == 2 && k.t() < GANK_AVANT_MS && ennemis.stream().anyMatch(e -> "JUNGLE".equals(poste.get(e)))) {
            gank.merge(k.victim(), 1.0, Double::sum);
        } else if (ennemis.size() == 1 && poste.get(ennemis.iterator().next()) != null
                && poste.get(ennemis.iterator().next()).equals(poste.get(k.victim()))) {
            echanges.merge(k.victim(), 1.0, Double::sum);
        }
    }

    static List<List<Kill>> combats(List<Kill> kills) {
        List<List<Kill>> combats = new ArrayList<>();
        List<Kill> courant = new ArrayList<>();
        for (Kill k : kills) {
            if (!courant.isEmpty()) {
                double cx = courant.stream().mapToDouble(Kill::x).average().orElse(0);
                double cy = courant.stream().mapToDouble(Kill::y).average().orElse(0);
                if (k.t() - courant.get(courant.size() - 1).t() <= COMBAT_ECART_MS
                        && Math.hypot(k.x() - cx, k.y() - cy) <= COMBAT_RAYON) {
                    courant.add(k);
                    continue;
                }
                if (courant.size() >= COMBAT_MINIMUM) {
                    combats.add(courant);
                }
            }
            courant = new ArrayList<>(List.of(k));
        }
        if (courant.size() >= COMBAT_MINIMUM) {
            combats.add(courant);
        }
        return combats;
    }

    // BUILDING_KILL et plaques portent l'équipe du bâtiment : c'est l'autre camp qui l'a pris.
    private static boolean echange(List<Document> autres, Document objectif, int camp, long t) {
        return autres.stream().filter(x -> x != objectif && Math.abs(temps(x) - t) <= ECHANGE_MS).anyMatch(x ->
                "ELITE_MONSTER_KILL".equals(x.getString("type"))
                        ? Integer.valueOf(camp).equals(x.getInteger("killerTeamId"))
                        : x.getInteger("teamId") != null && x.getInteger("teamId") != camp);
    }

    private static Document derniereImage(List<Document> frames, long t) {
        Document image = null;
        for (Document f : frames) {
            if (temps(f) <= t) {
                image = f;
            }
        }
        return image;
    }

    private static Document positionA(Document image, int pid) {
        Document pf = image.get("participantFrames", Document.class);
        Document joueur = pf == null ? null : pf.get(String.valueOf(pid), Document.class);
        return joueur == null ? null : joueur.get("position", Document.class);
    }

    private static double distance(Document a, Document b) {
        return a == null || b == null ? Double.MAX_VALUE
                : Math.hypot(nombre(a.get("x")) - nombre(b.get("x")), nombre(a.get("y")) - nombre(b.get("y")));
    }

    private static long temps(Document d) {
        return d.get("timestamp") instanceof Number n ? n.longValue() : 0;
    }

    private static double nombre(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
