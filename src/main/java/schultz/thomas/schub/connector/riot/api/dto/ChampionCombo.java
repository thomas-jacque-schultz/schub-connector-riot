package schultz.thomas.schub.connector.riot.api.dto;

import java.util.List;

// championIds dans l'ordre des emplacements de la requête.
public record ChampionCombo(List<Integer> championIds, long games, long wins) {
}
