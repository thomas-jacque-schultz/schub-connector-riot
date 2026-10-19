package schultz.thomas.schub.connector.riot.data.model;

import java.util.List;

public record ChampionCatalog(
        String version,
        String locale,
        List<ChampionCard> champions
) {
}
