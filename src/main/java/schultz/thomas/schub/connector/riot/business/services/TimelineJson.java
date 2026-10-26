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

    static long nombre(Map<String, Object> objet, String champ) {
        return objet.get(champ) instanceof Number n ? n.longValue() : 0L;
    }
}
