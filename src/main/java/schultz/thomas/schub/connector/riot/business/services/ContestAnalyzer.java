package schultz.thomas.schub.connector.riot.business.services;

import schultz.thomas.schub.connector.riot.business.stats.MatchSignalsService;
import schultz.thomas.schub.connector.riot.business.stats.MatchSignalsService.Kill;
import schultz.thomas.schub.connector.riot.data.model.Contest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.enObjet;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.liste;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.nombre;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.objet;

// Qui était là aux objectifs neutres et aux combats dans les jungles (Schub#156). Les positions ne sont connues
// qu'à la minute : est présent qui est à portée sur l'image d'avant ou sur celle d'après.
final class ContestAnalyzer {

    static final double PRESENCE = MatchSignalsService.OBJECTIF_GROUPE;
    private static final long LARVES_MS = 90_000L;
    private static final double DIAGONALE = 14_800;
    private static final double BANDE_RIVIERE = 1_500;
    private static final double BASE = 4_500;
    private static final Set<String> OBJECTIFS = Set.of("DRAGON", "HORDE", "RIFTHERALD", "BARON_NASHOR", "ATAKHAN");

    private ContestAnalyzer() {
    }

    static List<Contest> analyse(Map<String, Object> raw, ProximityAnalyzer.Lecture lecture) {
        if (lecture == null) {
            return List.of();
        }
        List<Kill> kills = new ArrayList<>();
        List<Monstre> monstres = new ArrayList<>();
        for (Object frame : liste(objet(raw, "info"), "frames")) {
            for (Object brut : liste(enObjet(frame), "events")) {
                Map<String, Object> event = enObjet(brut);
                Map<String, Object> position = objet(event, "position");
                if (position.isEmpty()) {
                    continue;
                }
                long t = nombre(event, "timestamp");
                double x = nombre(position, "x");
                double y = nombre(position, "y");
                if ("CHAMPION_KILL".equals(event.get("type"))) {
                    List<Integer> aides = new ArrayList<>();
                    liste(event, "assistingParticipantIds").forEach(id -> {
                        if (id instanceof Number n) {
                            aides.add(n.intValue());
                        }
                    });
                    kills.add(new Kill((int) nombre(event, "killerId"), (int) nombre(event, "victimId"), t, x, y,
                            aides, List.of()));
                } else if ("ELITE_MONSTER_KILL".equals(event.get("type"))
                        && OBJECTIFS.contains(String.valueOf(event.get("monsterType")))) {
                    monstres.add(new Monstre(t, String.valueOf(event.get("monsterType")),
                            (int) nombre(event, "killerTeamId"), x, y));
                }
            }
        }
        kills.sort(Comparator.comparingLong(Kill::t));
        monstres.sort(Comparator.comparingLong(Monstre::t));

        List<Contest> contests = new ArrayList<>(objectifs(monstres, lecture));
        for (List<Kill> combat : MatchSignalsService.combats(kills, 1)) {
            double x = combat.stream().mapToDouble(Kill::x).average().orElse(0);
            double y = combat.stream().mapToDouble(Kill::y).average().orElse(0);
            Integer proprietaire = jungle(x, y);
            if (proprietaire == null) {
                continue;
            }
            Set<String> presents = new LinkedHashSet<>();
            int victoires = 0;
            int defaites = 0;
            for (Kill kill : combat) {
                Integer campVictime = lecture.camps().get(kill.victim());
                if (campVictime == null) {
                    continue;
                }
                if (campVictime == proprietaire) {
                    defaites++;
                } else {
                    victoires++;
                }
                ajoute(presents, lecture, kill.victim());
                ajoute(presents, lecture, kill.killer());
                kill.assists().forEach(id -> ajoute(presents, lecture, id));
            }
            presents.addAll(aPortee(lecture, combat.getFirst().t(), combat.getLast().t(), x, y));
            contests.add(new Contest((int) (combat.getFirst().t() / 1000), Contest.JUNGLE_FIGHT, proprietaire,
                    victoires, defaites, List.copyOf(presents)));
        }
        contests.sort(Comparator.comparingInt(Contest::second));
        return contests;
    }

    // Les larves d'une même apparition tombent en quelques dizaines de secondes : un seul objectif, au camp qui en
    // a pris le plus.
    private static List<Contest> objectifs(List<Monstre> monstres, ProximityAnalyzer.Lecture lecture) {
        List<Contest> objectifs = new ArrayList<>();
        int i = 0;
        while (i < monstres.size()) {
            Monstre premier = monstres.get(i);
            List<Monstre> groupe = new ArrayList<>(List.of(premier));
            int j = i + 1;
            while ("HORDE".equals(premier.type()) && j < monstres.size() && "HORDE".equals(monstres.get(j).type())
                    && monstres.get(j).t() - groupe.getLast().t() <= LARVES_MS) {
                groupe.add(monstres.get(j++));
            }
            Map<Integer, Integer> parCamp = new HashMap<>();
            Set<String> presents = new LinkedHashSet<>();
            for (Monstre m : groupe) {
                parCamp.merge(m.camp(), 1, Integer::sum);
                presents.addAll(aPortee(lecture, m.t(), m.t(), m.x(), m.y()));
            }
            int preneur = parCamp.getOrDefault(100, 0) > parCamp.getOrDefault(200, 0) ? 100
                    : parCamp.getOrDefault(200, 0) > parCamp.getOrDefault(100, 0) ? 200 : groupe.getLast().camp();
            objectifs.add(new Contest((int) (premier.t() / 1000), premier.type(), preneur, 0, 0, List.copyOf(presents)));
            i = j;
        }
        return objectifs;
    }

    // Hors couloirs (même découpage que les ganks), hors rivière et hors bases ; le camp dont c'est la jungle.
    static Integer jungle(double x, double y) {
        if (EarlyGameAnalyzer.zone(x, y) != null) {
            return null;
        }
        double ecart = x + y - DIAGONALE;
        if (Math.abs(ecart) < BANDE_RIVIERE
                || (x < BASE && y < BASE) || (x > DIAGONALE - BASE && y > DIAGONALE - BASE)) {
            return null;
        }
        return ecart < 0 ? 100 : 200;
    }

    private static Set<String> aPortee(ProximityAnalyzer.Lecture lecture, long debut, long fin, double x, double y) {
        ProximityAnalyzer.Image avant = null;
        ProximityAnalyzer.Image apres = null;
        for (ProximityAnalyzer.Image image : lecture.images()) {
            if (image.ts() <= debut) {
                avant = image;
            } else if (image.ts() > fin && apres == null) {
                apres = image;
            }
        }
        Set<String> presents = new LinkedHashSet<>();
        for (ProximityAnalyzer.Image image : new ProximityAnalyzer.Image[] {avant, apres}) {
            if (image != null) {
                image.enJeu().forEach((id, p) -> {
                    if (Math.hypot(p[0] - x, p[1] - y) <= PRESENCE) {
                        presents.add(lecture.puuids().get(id));
                    }
                });
            }
        }
        return presents;
    }

    private static void ajoute(Set<String> presents, ProximityAnalyzer.Lecture lecture, int id) {
        String puuid = lecture.puuids().get(id);
        if (puuid != null) {
            presents.add(puuid);
        }
    }

    private record Monstre(long t, String type, int camp, double x, double y) {
    }
}
