package schultz.thomas.schub.connector.riot.business.services;

import schultz.thomas.schub.connector.riot.business.model.MatchParticipant;
import schultz.thomas.schub.connector.riot.data.model.Isolation;
import schultz.thomas.schub.connector.riot.data.model.Proximity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.enObjet;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.liste;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.nombre;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.objet;

final class ProximityAnalyzer {

    static final double ENSEMBLE = 2_000;
    private static final long QUINZE_MS = 15 * 60_000L;
    private static final double RAYON_FONTAINE = 1_200;
    private static final Map<Integer, double[]> FONTAINES = Map.of(100, new double[] {550, 580},
            200, new double[] {14_300, 14_390});
    // Temps de réapparition de base par niveau (wiki LoL, 2024). La timeline ne dit pas qui est mort à l'instant
    // de l'image : un joueur tué depuis moins que ce temps est compté mort.
    private static final double[] REAPPARITION_S = {10, 10, 12, 12, 14, 16, 20, 25, 28, 32.5, 35, 37.5, 40, 42.5, 45,
            47.5, 50, 52.5};

    private ProximityAnalyzer() {
    }

    static List<Proximity> analyse(Map<String, Object> raw, List<MatchParticipant> participants) {
        return analyse(lire(raw, participants));
    }

    static List<Proximity> analyse(Lecture lecture) {
        if (lecture == null) {
            return List.of();
        }
        List<Proximity> paires = new ArrayList<>();
        List<Integer> ids = lecture.puuids().keySet().stream().sorted().toList();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                int a = ids.get(i);
                int b = ids.get(j);
                if (lecture.camps().get(a).equals(lecture.camps().get(b))) {
                    paires.add(paire(a, b, lecture.puuids(), lecture.camps().get(a), lecture.images()));
                }
            }
        }
        return paires;
    }

    static List<Isolation> isolements(Lecture lecture) {
        if (lecture == null) {
            return List.of();
        }
        List<Isolation> isolements = new ArrayList<>();
        lecture.puuids().keySet().stream().sorted().forEach(id -> {
            int camp = lecture.camps().get(id);
            int[] compte = new int[4];
            for (Image image : lecture.images()) {
                double[] moi = image.enJeu().get(id);
                if (moi == null) {
                    continue;
                }
                boolean seul = image.enJeu().entrySet().stream()
                        .noneMatch(e -> !e.getKey().equals(id) && lecture.camps().get(e.getKey()) == camp
                                && Math.hypot(e.getValue()[0] - moi[0], e.getValue()[1] - moi[1]) < ENSEMBLE);
                int periode = image.ts() < QUINZE_MS ? 0 : 2;
                compte[periode]++;
                compte[periode + 1] += seul ? 1 : 0;
            }
            isolements.add(new Isolation(lecture.puuids().get(id), camp, compte[0], compte[1], compte[2], compte[3]));
        });
        return isolements;
    }

    // null : timeline vide, ou un joueur de la timeline absent du détail.
    static Lecture lire(Map<String, Object> raw, List<MatchParticipant> participants) {
        List<Object> puuids = liste(objet(raw, "metadata"), "participants");
        List<Object> frames = liste(objet(raw, "info"), "frames");
        if (puuids.isEmpty() || frames.isEmpty()) {
            return null;
        }
        Map<String, Integer> camps = new HashMap<>();
        participants.forEach(participant -> camps.put(participant.puuid(), participant.teamId()));
        Map<Integer, String> puuidParId = new HashMap<>();
        Map<Integer, Integer> campParId = new HashMap<>();
        for (int index = 0; index < puuids.size(); index++) {
            String puuid = String.valueOf(puuids.get(index));
            Integer camp = camps.get(puuid);
            if (camp == null) {
                return null;
            }
            puuidParId.put(index + 1, puuid);
            campParId.put(index + 1, camp);
        }

        Map<Integer, List<Long>> morts = new HashMap<>();
        for (Object frame : frames) {
            for (Object brut : liste(enObjet(frame), "events")) {
                Map<String, Object> event = enObjet(brut);
                if ("CHAMPION_KILL".equals(event.get("type"))) {
                    morts.computeIfAbsent((int) nombre(event, "victimId"), id -> new ArrayList<>())
                            .add(nombre(event, "timestamp"));
                }
            }
        }

        List<Image> images = new ArrayList<>();
        for (Object brut : frames) {
            Map<String, Object> frame = enObjet(brut);
            long ts = nombre(frame, "timestamp");
            if (ts == 0) {
                continue;
            }
            Map<Integer, double[]> enJeu = new HashMap<>();
            objet(frame, "participantFrames").forEach((cle, valeur) -> {
                int id = Integer.parseInt(cle);
                Map<String, Object> joueur = enObjet(valeur);
                Map<String, Object> position = objet(joueur, "position");
                Integer camp = campParId.get(id);
                if (position.isEmpty() || camp == null) {
                    return;
                }
                double[] p = {nombre(position, "x"), nombre(position, "y")};
                double[] fontaine = FONTAINES.get(camp);
                boolean aLaFontaine = fontaine != null
                        && Math.hypot(p[0] - fontaine[0], p[1] - fontaine[1]) < RAYON_FONTAINE;
                if (!aLaFontaine && !mort(morts.get(id), ts, (int) nombre(joueur, "level"))) {
                    enJeu.put(id, p);
                }
            });
            images.add(new Image(ts, enJeu));
        }
        return new Lecture(puuidParId, campParId, images);
    }

    private static Proximity paire(int a, int b, Map<Integer, String> puuids, int camp, List<Image> images) {
        int avant = 0;
        int ensembleAvant = 0;
        int apres = 0;
        int ensembleApres = 0;
        int departs = 0;
        double distances = 0;
        Boolean precedente = null;
        for (Image image : images) {
            double[] pa = image.enJeu().get(a);
            double[] pb = image.enJeu().get(b);
            if (pa == null || pb == null) {
                precedente = null;
                continue;
            }
            double distance = Math.hypot(pa[0] - pb[0], pa[1] - pb[1]);
            boolean ensemble = distance < ENSEMBLE;
            distances += distance;
            if (image.ts() < QUINZE_MS) {
                avant++;
                ensembleAvant += ensemble ? 1 : 0;
                if (Boolean.TRUE.equals(precedente) && !ensemble) {
                    departs++;
                }
                precedente = ensemble;
            } else {
                apres++;
                ensembleApres += ensemble ? 1 : 0;
            }
        }
        int comptees = avant + apres;
        return new Proximity(puuids.get(a), puuids.get(b), camp, avant, ensembleAvant, apres, ensembleApres,
                comptees == 0 ? null : (int) Math.round(distances / comptees), departs);
    }

    // Au-delà de 15 min, le temps de réapparition s'allonge jusqu'à +50 % à 55 min.
    static boolean mort(List<Long> morts, long ts, int niveau) {
        if (morts == null) {
            return false;
        }
        double base = REAPPARITION_S[Math.clamp(niveau, 1, REAPPARITION_S.length) - 1];
        double minutes = ts / 60_000.0;
        long attente = (long) (base * (1 + Math.clamp((minutes - 15) / 80, 0, 0.5)) * 1000);
        return morts.stream().anyMatch(t -> t <= ts && ts - t < attente);
    }

    // Les joueurs en jeu à chaque image : ni morts, ni à leur fontaine.
    record Lecture(Map<Integer, String> puuids, Map<Integer, Integer> camps, List<Image> images) {
    }

    record Image(long ts, Map<Integer, double[]> enJeu) {
    }
}
