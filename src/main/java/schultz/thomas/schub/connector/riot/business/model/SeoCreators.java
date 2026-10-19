package schultz.thomas.schub.connector.riot.business.model;

import java.util.List;

// unknown : les Riot ID que Riot ne connaît pas, laissés hors de la liste.
public record SeoCreators(List<String> creators, List<String> unknown) {
}
