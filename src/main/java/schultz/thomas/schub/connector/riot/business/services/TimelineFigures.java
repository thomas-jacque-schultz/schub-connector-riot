package schultz.thomas.schub.connector.riot.business.services;

import schultz.thomas.schub.connector.riot.data.model.MatchInsights;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.enObjet;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.liste;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.nombre;
import static schultz.thomas.schub.connector.riot.business.services.TimelineJson.objet;

final class TimelineFigures {

    static final long QUINZE_MINUTES_MS = 15 * 60_000L;

    private TimelineFigures() {
    }

    // participantId 1 à 10 dans l'ordre de metadata.participants ; une image par minute.
    static Map<String, MatchInsights.At15> a15(Map<String, Object> raw) {
        Map<String, Object> metadata = objet(raw, "metadata");
        Map<String, Object> info = objet(raw, "info");
        List<Object> puuids = liste(metadata, "participants");
        List<Object> frames = liste(info, "frames");
        if (puuids.isEmpty() || frames.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> image = frames.stream()
                .map(TimelineJson::enObjet)
                .filter(frame -> nombre(frame, "timestamp") >= QUINZE_MINUTES_MS)
                .findFirst()
                .orElse(null);
        if (image == null) {
            return Map.of();
        }

        Map<Integer, int[]> kda = new HashMap<>();
        for (Object frame : frames) {
            for (Object brut : liste(enObjet(frame), "events")) {
                Map<String, Object> event = enObjet(brut);
                if (!"CHAMPION_KILL".equals(event.get("type")) || nombre(event, "timestamp") > QUINZE_MINUTES_MS) {
                    continue;
                }
                kda.computeIfAbsent((int) nombre(event, "killerId"), id -> new int[3])[0]++;
                kda.computeIfAbsent((int) nombre(event, "victimId"), id -> new int[3])[1]++;
                for (Object aide : liste(event, "assistingParticipantIds")) {
                    if (aide instanceof Number id) {
                        kda.computeIfAbsent(id.intValue(), cle -> new int[3])[2]++;
                    }
                }
            }
        }

        Map<String, Object> participantFrames = objet(image, "participantFrames");
        Map<String, MatchInsights.At15> parPuuid = new HashMap<>();
        for (int index = 0; index < puuids.size(); index++) {
            int participantId = index + 1;
            Map<String, Object> pf = objet(participantFrames, String.valueOf(participantId));
            if (pf.isEmpty()) {
                continue;
            }
            Map<String, Object> degats = objet(pf, "damageStats");
            int[] siens = kda.getOrDefault(participantId, new int[3]);
            parPuuid.put(String.valueOf(puuids.get(index)), new MatchInsights.At15(
                    (int) nombre(pf, "totalGold"),
                    (int) nombre(pf, "xp"),
                    (int) (nombre(pf, "minionsKilled") + nombre(pf, "jungleMinionsKilled")),
                    (int) nombre(degats, "totalDamageDoneToChampions"),
                    siens[0], siens[1], siens[2]));
        }
        return parPuuid;
    }

    // La dernière image de la timeline tombe sur la fin de la partie.
    static Map<String, MatchInsights.AtEnd> aLaFin(Map<String, Object> raw) {
        List<Object> puuids = liste(objet(raw, "metadata"), "participants");
        List<Object> frames = liste(objet(raw, "info"), "frames");
        if (puuids.isEmpty() || frames.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> kills = new HashMap<>();
        for (Object frame : frames) {
            for (Object brut : liste(enObjet(frame), "events")) {
                Map<String, Object> event = enObjet(brut);
                if ("CHAMPION_KILL".equals(event.get("type"))) {
                    kills.merge((int) nombre(event, "killerId"), 1, Integer::sum);
                }
            }
        }
        Map<String, Object> participantFrames = objet(enObjet(frames.getLast()), "participantFrames");
        Map<String, MatchInsights.AtEnd> parPuuid = new HashMap<>();
        for (int index = 0; index < puuids.size(); index++) {
            int participantId = index + 1;
            Map<String, Object> pf = objet(participantFrames, String.valueOf(participantId));
            if (!pf.isEmpty()) {
                parPuuid.put(String.valueOf(puuids.get(index)), new MatchInsights.AtEnd((int) nombre(pf, "totalGold"),
                        (int) nombre(pf, "xp"), kills.getOrDefault(participantId, 0)));
            }
        }
        return parPuuid;
    }

    static List<MatchInsights.EndObjectives> objectifsDeFin(Map<String, Object> raw) {
        List<Object> frames = liste(objet(raw, "info"), "frames");
        if (frames.isEmpty()) {
            return List.of();
        }
        Map<Integer, int[]> parCamp = new LinkedHashMap<>();
        parCamp.put(100, new int[2]);
        parCamp.put(200, new int[2]);
        for (Object frame : frames) {
            for (Object brut : liste(enObjet(frame), "events")) {
                Map<String, Object> event = enObjet(brut);
                int[] camp = parCamp.get((int) nombre(event, "killerTeamId"));
                if (camp == null || !"ELITE_MONSTER_KILL".equals(event.get("type"))) {
                    continue;
                }
                if ("DRAGON".equals(event.get("monsterType"))) {
                    camp[0]++;
                } else if ("RIFTHERALD".equals(event.get("monsterType"))) {
                    camp[1]++;
                }
            }
        }
        return parCamp.entrySet().stream()
                .map(e -> new MatchInsights.EndObjectives(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }
}
