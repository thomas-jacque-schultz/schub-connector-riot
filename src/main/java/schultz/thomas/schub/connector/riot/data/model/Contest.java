package schultz.thomas.schub.connector.riot.data.model;

import java.util.List;

/**
 * Un objectif neutre pris (kind = monsterType, side = le camp qui l'a pris), ou un combat dans une jungle
 * (kind = JUNGLE_FIGHT, side = le camp dont c'est la jungle), et les joueurs des deux camps qui étaient là.
 */
public record Contest(
        int second,
        String kind,
        int side,
        int sideKills,
        int sideDeaths,
        List<String> present
) {

    public static final String JUNGLE_FIGHT = "JUNGLE_FIGHT";
}
