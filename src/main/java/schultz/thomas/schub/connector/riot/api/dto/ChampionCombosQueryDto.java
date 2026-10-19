package schultz.thomas.schub.connector.riot.api.dto;

import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.util.List;

// Un combo = un champion par emplacement, dans le même camp. Deux ou trois emplacements, postes distincts.
public record ChampionCombosQueryDto(List<Slot> slots, int minGames) {

    public record Slot(TeamPosition position, List<Integer> championIds) {
    }
}
