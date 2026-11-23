package schultz.thomas.schub.connector.riot.business.services;

import java.util.List;
import java.util.Map;

// Le brut fraîchement reçu porte des Map imbriquées, celui relu de Mongo des Document : on lit des Map.
final class TimelineJson {

    private TimelineJson() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> enObjet(Object valeur) {
        return valeur instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    static Map<String, Object> objet(Map<String, Object> parent, String champ) {
        return enObjet(parent.get(champ));
    }

    @SuppressWarnings("unchecked")
    static List<Object> liste(Map<String, Object> parent, String champ) {
        return parent.get(champ) instanceof List<?> liste ? (List<Object>) liste : List.of();
    }

    /*
     * Le puuid de chaque participantId, rangé par participantId (indice 0 = participantId 1). info.participants porte
     * la correspondance ; metadata.participants n'est pas toujours dans cet ordre (1 timeline sur 40 le 23/11).
     */
    static List<Object> puuids(Map<String, Object> timeline) {
        List<Object> metadata = liste(objet(timeline, "metadata"), "participants");
        List<Object> correspondance = liste(objet(timeline, "info"), "participants");
        if (correspondance.size() != metadata.size()) {
            return metadata;
        }
        Object[] ranges = new Object[metadata.size()];
        for (Object brut : correspondance) {
            Map<String, Object> participant = enObjet(brut);
            int id = (int) nombre(participant, "participantId");
            if (id < 1 || id > ranges.length || ranges[id - 1] != null || participant.get("puuid") == null) {
                return metadata;
            }
            ranges[id - 1] = participant.get("puuid");
        }
        return List.of(ranges);
    }

    static long nombre(Map<String, Object> objet, String champ) {
        return objet.get(champ) instanceof Number n ? n.longValue() : 0L;
    }
}
