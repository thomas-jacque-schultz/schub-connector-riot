package schultz.thomas.schub.connector.riot.business.services;

import schultz.thomas.schub.connector.riot.data.model.MatchInsights.Gold;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.enObjet;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.liste;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.nombre;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.objet;

/*
 * D'où vient l'or de chaque joueur, et ce que ses morts ont donné (Schub#179).
 * Riot ne rend que l'or total par minute. Les primes de kill et de série sont lues dans l'événement ; le reste
 * vaut un prix par unité, calé le 23/11 par régression de l'or gagné entre deux images (38 ranked, patch 16.22,
 * R² 0,955). bounty vaut 0 sur les tours et les monstres épiques : leur or ne se lit pas. Le total retombe sur
 * l'or réel : l'écart est réparti sur les lignes estimées, jamais sur les lignes exactes.
 */
final class GoldAnalyzer {

    private static final int OR_DE_DEPART = 500;
    private static final double PASSIF_PAR_SECONDE = 2.22;
    private static final double DEBUT_DU_PASSIF_S = 65;
    private static final double[] SBIRE = {18.77, 21.32, 25.88};
    private static final double[] MONSTRE = {22.92, 24.98, 26.16};
    private static final double ASSISTANCE_PART_DE_PRIME = 0.48;
    private static final double ASSISTANCE_FIXE = 14.7;
    private static final double OBJET_DE_SUPPORT_PAR_MINUTE = 40.0;
    private static final int QUETE_DU_TIREUR = 329;
    private static final int OBJET_QUETE_DU_TIREUR = 1202;
    private static final double PLAQUE_DETRUITE = 89.5;
    private static final double PLAQUE_A_COTE = 17.4;
    private static final double PRES_D_UNE_PLAQUE = 1500;
    // Par structure : {destructeur, assistant, chaque membre du camp}. Les coefficients négatifs du calage valent 0.
    private static final Map<String, double[]> STRUCTURE = Map.of(
            "OUTER_TURRET", new double[]{47.8, 33.7, 59.4},
            "INNER_TURRET", new double[]{56.4, 54.1, 28.9},
            "BASE_TURRET", new double[]{0, 42.6, 38.3},
            "NEXUS_TURRET", new double[]{16.9, 0, 56.1},
            "INHIBITOR", new double[]{73.5, 0, 3.6});
    // Par monstre : {tueur, chaque membre du camp}.
    private static final Map<String, double[]> MONSTRE_EPIQUE = Map.of(
            "BARON_NASHOR", new double[]{11.7, 149.5},
            "DRAGON", new double[]{0, 9.8},
            "HORDE", new double[]{0, 3.1},
            "RIFTHERALD", new double[]{14.2, 0});
    private static final Set<String> NEXUS_ET_INHIBITEURS = Set.of("NEXUS_TURRET", "INHIBITOR");

    private GoldAnalyzer() {
    }

    /** Par puuid. Vide sans timeline ou sans la partie. */
    static Map<String, Gold> analyse(Map<String, Object> timeline, Map<String, Object> match) {
        List<Object> puuids = TimelineJson.puuids(timeline);
        List<Map<String, Object>> images = liste(objet(timeline, "info"), "frames").stream()
                .map(TimelineJson::enObjet).toList();
        List<Object> joueurs = liste(objet(match, "info"), "participants");
        if (puuids.isEmpty() || images.size() < 2 || joueurs.size() != puuids.size()) {
            return Map.of();
        }
        int n = puuids.size();
        Lignes[] lignes = new Lignes[n + 1];
        for (int id = 1; id <= n; id++) {
            lignes[id] = new Lignes();
        }
        Map<Integer, List<Gold.Death>> morts = new HashMap<>();

        long fin = nombre(images.getLast(), "timestamp");
        for (int id = 1; id <= n; id++) {
            lignes[id].passif = PASSIF_PAR_SECONDE * Math.max(0, fin / 1000.0 - DEBUT_DU_PASSIF_S);
        }
        for (int k = 0; k + 1 < images.size(); k++) {
            int palier = palier(nombre(images.get(k + 1), "timestamp"));
            for (int id = 1; id <= n; id++) {
                Map<String, Object> avant = objet(objet(images.get(k), "participantFrames"), String.valueOf(id));
                Map<String, Object> apres = objet(objet(images.get(k + 1), "participantFrames"), String.valueOf(id));
                lignes[id].sbires += SBIRE[palier] * Math.max(0, nombre(apres, "minionsKilled") - nombre(avant, "minionsKilled"));
                lignes[id].jungle += MONSTRE[palier]
                        * Math.max(0, nombre(apres, "jungleMinionsKilled") - nombre(avant, "jungleMinionsKilled"));
            }
        }

        for (Map<String, Object> image : images) {
            for (Object brut : liste(image, "events")) {
                Map<String, Object> event = enObjet(brut);
                switch (String.valueOf(event.get("type"))) {
                    case "CHAMPION_KILL" -> kill(event, lignes, morts, n);
                    case "BUILDING_KILL" -> structure(event, lignes, n);
                    case "TURRET_PLATE_DESTROYED" -> plaque(event, lignes, images, n);
                    case "ELITE_MONSTER_KILL" -> monstre(event, lignes, n);
                    case "ITEM_DESTROYED" -> {
                        int id = (int) nombre(event, "participantId");
                        if (nombre(event, "itemId") == OBJET_QUETE_DU_TIREUR && id >= 1 && id <= n) {
                            lignes[id].quete = QUETE_DU_TIREUR;
                        }
                    }
                    default -> {
                        // les autres événements ne rapportent pas d'or
                    }
                }
            }
        }

        int minutes = images.size() - 1;
        Map<String, Gold> parPuuid = new HashMap<>();
        for (int id = 1; id <= n; id++) {
            Map<String, Object> joueur = joueurDe(joueurs, id);
            Lignes l = lignes[id];
            if ("UTILITY".equals(joueur.get("teamPosition"))) {
                l.support = OBJET_DE_SUPPORT_PAR_MINUTE * minutes;
            }
            long gagne = nombre(joueur, "goldEarned");
            if (gagne <= 0) {
                gagne = nombre(objet(objet(images.getLast(), "participantFrames"), String.valueOf(id)), "totalGold");
            }
            parPuuid.put(String.valueOf(puuids.get(id - 1)),
                    l.enOr((int) gagne, joueur, morts.getOrDefault(id, List.of())));
        }
        return parPuuid;
    }

    private static Map<String, Object> joueurDe(List<Object> joueurs, int id) {
        return joueurs.stream().map(TimelineJson::enObjet)
                .filter(joueur -> nombre(joueur, "participantId") == id)
                .findFirst()
                .orElseGet(() -> enObjet(joueurs.get(id - 1)));
    }

    private static int palier(long timestamp) {
        long minute = timestamp / 60_000;
        return minute <= 14 ? 0 : minute <= 25 ? 1 : 2;
    }

    private static void kill(Map<String, Object> event, Lignes[] lignes, Map<Integer, List<Gold.Death>> morts, int n) {
        int tueur = (int) nombre(event, "killerId");
        int victime = (int) nombre(event, "victimId");
        long prime = nombre(event, "bounty");
        long serie = nombre(event, "shutdownBounty");
        List<Integer> aides = ids(event, n);
        if (tueur >= 1 && tueur <= n) {
            lignes[tueur].kills += prime;
            lignes[tueur].series += serie;
        }
        double partAide = aides.isEmpty() ? 0 : ASSISTANCE_PART_DE_PRIME * prime / aides.size() + ASSISTANCE_FIXE;
        aides.forEach(id -> lignes[id].assists += partAide);
        if (victime >= 1 && victime <= n) {
            double base = prime + partAide * aides.size();
            lignes[victime].cedeBase += base;
            lignes[victime].cedeSerie += serie;
            morts.computeIfAbsent(victime, id -> new ArrayList<>()).add(new Gold.Death(
                    (int) (nombre(event, "timestamp") / 1000), (int) Math.round(base + serie), (int) serie));
        }
    }

    private static void structure(Map<String, Object> event, Lignes[] lignes, int n) {
        String type = "INHIBITOR_BUILDING".equals(event.get("buildingType")) ? "INHIBITOR"
                : String.valueOf(event.get("towerType"));
        double[] prix = STRUCTURE.get(type);
        if (prix == null) {
            return;
        }
        int attaquants = 300 - (int) nombre(event, "teamId");
        int tueur = (int) nombre(event, "killerId");
        List<Integer> aides = ids(event, n);
        boolean nexus = NEXUS_ET_INHIBITEURS.contains(type);
        for (int id = 1; id <= n; id++) {
            if (camp(id, n) != attaquants) {
                continue;
            }
            double or = prix[2] + (id == tueur ? prix[0] : aides.contains(id) ? prix[1] : 0);
            if (nexus) {
                lignes[id].inhibiteurs += or;
            } else {
                lignes[id].tours += or;
            }
        }
    }

    private static void plaque(Map<String, Object> event, Lignes[] lignes, List<Map<String, Object>> images, int n) {
        int attaquants = 300 - (int) nombre(event, "teamId");
        int tueur = (int) nombre(event, "killerId");
        long t = nombre(event, "timestamp");
        Map<String, Object> lieu = objet(event, "position");
        int apres = 1;
        while (apres < images.size() - 1 && nombre(images.get(apres), "timestamp") < t) {
            apres++;
        }
        for (int id = 1; id <= n; id++) {
            if (camp(id, n) != attaquants) {
                continue;
            }
            if (id == tueur) {
                lignes[id].plaques += PLAQUE_DETRUITE;
            } else if (Math.min(distance(images.get(apres - 1), id, lieu), distance(images.get(apres), id, lieu))
                    < PRES_D_UNE_PLAQUE) {
                lignes[id].plaques += PLAQUE_A_COTE;
            }
        }
    }

    private static void monstre(Map<String, Object> event, Lignes[] lignes, int n) {
        double[] prix = MONSTRE_EPIQUE.get(String.valueOf(event.get("monsterType")));
        if (prix == null) {
            return;
        }
        int camp = (int) nombre(event, "killerTeamId");
        int tueur = (int) nombre(event, "killerId");
        for (int id = 1; id <= n; id++) {
            if (camp(id, n) == camp) {
                lignes[id].objectifs += prix[1] + (id == tueur ? prix[0] : 0);
            }
        }
    }

    private static double distance(Map<String, Object> image, int id, Map<String, Object> lieu) {
        Map<String, Object> position = objet(objet(objet(image, "participantFrames"), String.valueOf(id)), "position");
        if (position.isEmpty() || lieu.isEmpty()) {
            return Double.MAX_VALUE;
        }
        return Math.hypot(nombre(position, "x") - nombre(lieu, "x"), nombre(position, "y") - nombre(lieu, "y"));
    }

    private static int camp(int id, int n) {
        return id <= n / 2 ? 100 : 200;
    }

    private static List<Integer> ids(Map<String, Object> event, int n) {
        return liste(event, "assistingParticipantIds").stream()
                .filter(Number.class::isInstance).map(id -> ((Number) id).intValue())
                .filter(id -> id >= 1 && id <= n).toList();
    }

    private static final class Lignes {
        double passif;
        double sbires;
        double jungle;
        double support;
        double quete;
        double plaques;
        double tours;
        double inhibiteurs;
        double objectifs;
        double kills;
        double series;
        double assists;
        double cedeBase;
        double cedeSerie;

        Gold enOr(int gagne, Map<String, Object> joueur, List<Gold.Death> morts) {
            long allie = nombre(joueur, "totalAllyJungleMinionsKilled");
            long adverse = nombre(joueur, "totalEnemyJungleMinionsKilled");
            long riviere = Math.max(0, nombre(joueur, "neutralMinionsKilled") - allie - adverse);
            long camps = allie + adverse + riviere;
            double[] exactes = {OR_DE_DEPART, passif, quete, kills, series};
            double[] estimees = {sbires, camps == 0 ? jungle : jungle * allie / camps,
                    camps == 0 ? 0 : jungle * adverse / camps, camps == 0 ? 0 : jungle * riviere / camps,
                    support, plaques, tours, inhibiteurs, objectifs, assists};
            double reste = gagne - sum(exactes);
            double estime = sum(estimees);
            double echelle = estime > 0 ? Math.max(0, reste) / estime : 0;
            int[] valeurs = new int[exactes.length + estimees.length];
            for (int i = 0; i < exactes.length; i++) {
                valeurs[i] = (int) Math.round(exactes[i]);
            }
            int plusGrande = exactes.length;
            for (int i = 0; i < estimees.length; i++) {
                valeurs[exactes.length + i] = (int) Math.round(estimees[i] * echelle);
                if (estimees[i] > estimees[plusGrande - exactes.length]) {
                    plusGrande = exactes.length + i;
                }
            }
            // L'arrondi ne doit pas faire mentir le total : le reste va à la plus grosse ligne estimée.
            int ecart = gagne - Arrays.stream(valeurs).sum();
            if (estime > 0) {
                valeurs[plusGrande] = Math.max(0, valeurs[plusGrande] + ecart);
            }
            return new Gold(gagne, valeurs[0], valeurs[1], valeurs[5], valeurs[6], valeurs[7], valeurs[8],
                    valeurs[9], valeurs[2], valeurs[10], valeurs[11], valeurs[12], valeurs[13], valeurs[3],
                    valeurs[4], valeurs[14], (int) Math.round(cedeBase), (int) Math.round(cedeSerie), List.copyOf(morts));
        }

        private static double sum(double[] valeurs) {
            double total = 0;
            for (double v : valeurs) {
                total += v;
            }
            return total;
        }
    }
}
