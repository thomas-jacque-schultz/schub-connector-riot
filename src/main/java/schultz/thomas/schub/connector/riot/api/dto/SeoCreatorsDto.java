package schultz.thomas.schub.connector.riot.api.dto;

import java.util.List;

// unknown : les Riot ID que Riot ne connaît pas, laissés hors de la liste.
public record SeoCreatorsDto(List<String> creators, List<String> unknown) {
}
