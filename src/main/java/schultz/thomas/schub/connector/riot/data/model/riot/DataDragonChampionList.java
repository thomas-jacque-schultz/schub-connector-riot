package schultz.thomas.schub.connector.riot.data.model.riot;

import java.util.List;
import java.util.Map;

public record DataDragonChampionList(String type, String format, String version,
                                     Map<String, Champion> data) {

    public record Champion(String id, String key, String name, String title, List<String> tags,
                           Image image) {
    }

    public record Image(String full, String group) {
    }
}
