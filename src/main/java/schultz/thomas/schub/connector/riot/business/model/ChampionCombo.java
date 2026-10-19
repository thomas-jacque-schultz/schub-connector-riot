package schultz.thomas.schub.connector.riot.business.model;

import java.util.List;

// championIds dans l'ordre des emplacements de la requête.
public record ChampionCombo(List<Integer> championIds, long games, long wins) {
}
