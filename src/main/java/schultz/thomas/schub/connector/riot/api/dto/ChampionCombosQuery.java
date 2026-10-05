package schultz.thomas.schub.connector.riot.api.dto;

import java.util.List;

// Un combo = un champion par emplacement, dans le même camp. Deux ou trois emplacements, postes distincts.
public record ChampionCombosQuery(List<Slot> slots, int minGames) {

    public record Slot(TeamPosition position, List<Integer> championIds) {
    }
}
