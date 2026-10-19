package schultz.thomas.schub.connector.riot.business.model;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.util.List;

// Un combo = un champion par emplacement, dans le même camp. Deux ou trois emplacements, postes distincts.
public record ChampionCombosQuery(List<Slot> slots, int minGames) {

    public record Slot(TeamPosition position, List<Integer> championIds) {
    }
}
