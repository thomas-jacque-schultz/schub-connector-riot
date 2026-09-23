package schultz.thomas.schub.connector.riot.business.services;

import schultz.thomas.schub.connector.riot.api.dto.EarlyGame;
import schultz.thomas.schub.connector.riot.api.dto.EarlyGame.Lane;
import schultz.thomas.schub.connector.riot.api.dto.EarlyGame.Outcome;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService.enObjet;
import static schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService.liste;
import static schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService.nombre;
import static schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService.objet;

// Coordonnées de la Faille : 0 à ~14 800 sur les deux axes, base bleue en bas à gauche, mid sur la diagonale.
final class EarlyGameAnalyzer {

    static final long FIN_MS = 15 * 60_000L;
    private static final long DEBUT_GANKS_MS = 150_000L;
    private static final long DEBUT_KILLS_MS = 90_000L;
    private static final double PORTEE = 2_000;
    private static final long FUSION_MS = 75_000L;
    private static final long AVANT_MS = 15_000L;
    private static final long APRES_MS = 20_000L;
    private static final long SUITE_OBJECTIF_MS = 90_000L;
    private static final double BANDE_MID = 1_500;
    private static final Set<String> COTE_HAUT = Set.of("HORDE", "RIFTHERALD");

    private EarlyGameAnalyzer() {
    }

    // null : postes inconnus, ou pas exactement un jungler par camp — un gank ne s'attribue alors à personne.
    static EarlyGame analyse(Map<String, Object> raw, List<MatchParticipant> participants) {
        List<Object> puuids = liste(objet(raw, "metadata"), "participants");
        List<Object> frames = liste(objet(raw, "info"), "frames");
        if (puuids.isEmpty() || frames.isEmpty()) {
            return null;
        }
        Map<String, MatchParticipant> parPuuid = new HashMap<>();
        participants.forEach(participant -> parPuuid.put(participant.puuid(), participant));
        Map<Integer, MatchParticipant> parId = new HashMap<>();
        for (int index = 0; index < puuids.size(); index++) {
            MatchParticipant participant = parPuuid.get(String.valueOf(puuids.get(index)));
            if (participant == null || participant.position() == TeamPosition.UNKNOWN) {
                return null;
            }
            parId.put(index + 1, participant);
        }
        Map<Integer, Integer> junglers = new HashMap<>();
        for (Map.Entry<Integer, MatchParticipant> entree : parId.entrySet()) {
            if (entree.getValue().position() == TeamPosition.JUNGLE
                    && junglers.put(entree.getValue().teamId(), entree.getKey()) != null) {
                return null;
            }
        }
        if (junglers.size() != 2) {
            return null;
        }

        List<Image> images = new ArrayList<>();
        List<Kill> kills = new ArrayList<>();
        List<Monstre> monstres = new ArrayList<>();
        for (Object brut : frames) {
            Map<String, Object> frame = enObjet(brut);
            long ts = nombre(frame, "timestamp");
            Map<Integer, Point> positions = new HashMap<>();
            objet(frame, "participantFrames").forEach((cle, valeur) -> {
                Map<String, Object> position = objet(enObjet(valeur), "position");
                if (!position.isEmpty()) {
                    positions.put(Integer.parseInt(cle), new Point(nombre(position, "x"), nombre(position, "y")));
                }
            });
            images.add(new Image(ts, positions));
            for (Object e : liste(frame, "events")) {
                Map<String, Object> event = enObjet(e);
                long t = nombre(event, "timestamp");
                if ("CHAMPION_KILL".equals(event.get("type")) && t <= FIN_MS) {
                    Map<String, Object> position = objet(event, "position");
                    List<Integer> aides = new ArrayList<>();
                    liste(event, "assistingParticipantIds").forEach(id -> {
                        if (id instanceof Number n) {
                            aides.add(n.intValue());
                        }
                    });
                    kills.add(new Kill(t, (int) nombre(event, "killerId"), (int) nombre(event, "victimId"), aides,
                            position.isEmpty() ? null : zone(nombre(position, "x"), nombre(position, "y"))));
                } else if ("ELITE_MONSTER_KILL".equals(event.get("type"))) {
                    monstres.add(new Monstre(t, (int) nombre(event, "killerTeamId"),
                            String.valueOf(event.get("monsterType"))));
                }
            }
        }

        List<EarlyGame.Gank> ganks = new ArrayList<>();
        List<EarlyGame.JunglePresence> presences = new ArrayList<>();
        List<EarlyGame.Objectives> objectifs = new ArrayList<>();
        for (int side : List.of(100, 200)) {
            int jungler = junglers.get(side);
            for (Lane lane : Lane.values()) {
                List<Integer> cibles = parId.entrySet().stream()
                        .filter(e -> e.getValue().teamId() != side && couloir(e.getValue().position()) == lane)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
                if (!cibles.isEmpty()) {
                    ganks.addAll(ganksSur(lane, side, jungler, cibles, images, kills, monstres, parId));
                }
            }
            presences.add(presence(parId.get(jungler), images.stream()
                    .filter(image -> image.ts() >= 2 * 60_000L && image.ts() <= 14 * 60_000L + 5_000L)
                    .map(image -> image.positions().get(jungler))
                    .toList()));
            objectifs.add(objectifs(side, monstres));
        }
        ganks.sort(Comparator.comparingInt(EarlyGame.Gank::second));
        return new EarlyGame(ganks, presences, objectifs);
    }

    private static List<EarlyGame.Gank> ganksSur(Lane lane, int side, int jungler, List<Integer> cibles,
                                                 List<Image> images, List<Kill> kills, List<Monstre> monstres,
                                                 Map<Integer, MatchParticipant> parId) {
        List<Moment> moments = new ArrayList<>();
        for (Image image : images) {
            Point j = image.positions().get(jungler);
            if (image.ts() < DEBUT_GANKS_MS || image.ts() > FIN_MS || j == null || zone(j.x(), j.y()) != lane) {
                continue;
            }
            boolean auContact = cibles.stream().map(image.positions()::get).anyMatch(c -> c != null
                    && zone(c.x(), c.y()) == lane && c.distance(j) < PORTEE);
            if (auContact) {
                moments.add(new Moment(image.ts(), false));
            }
        }
        for (Kill kill : kills) {
            if (kill.t() >= DEBUT_KILLS_MS && kill.zone() == lane && kill.implique(jungler)
                    && cibles.stream().anyMatch(kill::implique)) {
                moments.add(new Moment(kill.t(), true));
            }
        }
        moments.sort(Comparator.comparingLong(Moment::t));

        List<EarlyGame.Gank> ganks = new ArrayList<>();
        int i = 0;
        while (i < moments.size()) {
            long debut = moments.get(i).t();
            long fin = debut;
            Long premierKill = moments.get(i).kill() ? debut : null;
            int j = i + 1;
            while (j < moments.size() && moments.get(j).t() - fin <= FUSION_MS) {
                fin = moments.get(j).t();
                if (premierKill == null && moments.get(j).kill()) {
                    premierKill = fin;
                }
                j++;
            }
            long de = debut - AVANT_MS;
            long a = fin + APRES_MS;
            int pertesDefense = 0;
            int pertesAttaque = 0;
            List<String> morts = new ArrayList<>();
            for (Kill kill : kills) {
                if (kill.t() < de || kill.t() > a || kill.zone() != lane) {
                    continue;
                }
                MatchParticipant victime = parId.get(kill.victime());
                if (victime == null) {
                    continue;
                }
                morts.add(victime.puuid());
                if (victime.teamId() == side) {
                    pertesAttaque++;
                } else {
                    pertesDefense++;
                }
            }
            long finGank = fin;
            boolean objectif = monstres.stream().anyMatch(m -> m.equipe() == side && m.t() >= debut
                    && m.t() <= finGank + SUITE_OBJECTIF_MS && deCeCote(m.type(), lane));
            ganks.add(new EarlyGame.Gank(
                    (int) ((premierKill != null ? premierKill : debut) / 1000),
                    lane,
                    side,
                    parId.get(jungler).puuid(),
                    cibles.stream().map(id -> parId.get(id).puuid()).toList(),
                    issue(pertesDefense, pertesAttaque),
                    pertesDefense,
                    pertesAttaque,
                    morts,
                    objectif));
            i = j;
        }
        return ganks;
    }

    private static Outcome issue(int pertesDefense, int pertesAttaque) {
        if (pertesDefense > 0 && pertesAttaque > 0) {
            return Outcome.TRADE;
        }
        if (pertesDefense > 0) {
            return Outcome.KILL;
        }
        return pertesAttaque > 0 ? Outcome.COUNTER : Outcome.SURVIVED;
    }

    private static boolean deCeCote(String monstre, Lane lane) {
        return switch (lane) {
            case BOT -> "DRAGON".equals(monstre);
            case TOP -> COTE_HAUT.contains(monstre);
            case MID -> "DRAGON".equals(monstre) || COTE_HAUT.contains(monstre);
        };
    }

    private static EarlyGame.JunglePresence presence(MatchParticipant jungler, List<Point> positions) {
        int haut = 0;
        int milieu = 0;
        int bas = 0;
        for (Point p : positions) {
            if (p == null) {
                continue;
            }
            double ecart = p.y() - p.x();
            if (ecart > BANDE_MID) {
                haut++;
            } else if (ecart < -BANDE_MID) {
                bas++;
            } else {
                milieu++;
            }
        }
        return new EarlyGame.JunglePresence(jungler.puuid(), jungler.teamId(), haut, milieu, bas);
    }

    private static EarlyGame.Objectives objectifs(int side, List<Monstre> monstres) {
        List<Monstre> pris = monstres.stream().filter(m -> m.equipe() == side && m.t() <= FIN_MS).toList();
        return new EarlyGame.Objectives(side,
                (int) pris.stream().filter(m -> "DRAGON".equals(m.type())).count(),
                (int) pris.stream().filter(m -> "HORDE".equals(m.type())).count(),
                (int) pris.stream().filter(m -> "RIFTHERALD".equals(m.type())).count());
    }

    static Lane couloir(TeamPosition position) {
        return switch (position) {
            case TOP -> Lane.TOP;
            case MIDDLE -> Lane.MID;
            case BOTTOM, UTILITY -> Lane.BOT;
            default -> null;
        };
    }

    static Lane zone(double x, double y) {
        if ((x < 2_600 && y > 4_500) || (y > 12_300 && x < 10_300)) {
            return Lane.TOP;
        }
        if ((y < 2_600 && x > 4_500) || (x > 12_300 && y < 10_300)) {
            return Lane.BOT;
        }
        if (Math.abs(x - y) < 1_300 && x + y > 7_000 && x + y < 22_600) {
            return Lane.MID;
        }
        return null;
    }

    private record Point(double x, double y) {
        double distance(Point autre) {
            return Math.hypot(x - autre.x, y - autre.y);
        }
    }

    private record Image(long ts, Map<Integer, Point> positions) {
    }

    private record Kill(long t, int tueur, int victime, List<Integer> aides, Lane zone) {
        boolean implique(int id) {
            return tueur == id || victime == id || aides.contains(id);
        }
    }

    private record Moment(long t, boolean kill) {
    }

    private record Monstre(long t, int equipe, String type) {
    }
}
