package schultz.thomas.schub.connector.riot.data.model;

import java.util.List;

public record ChampionCard(
        String key,
        int id,
        String name,
        String title,
        List<String> tags,
        String iconUrl
) {
}
