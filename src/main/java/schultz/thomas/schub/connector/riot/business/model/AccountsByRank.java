package schultz.thomas.schub.connector.riot.business.model;

import java.util.List;

public record AccountsByRank(List<Row> rows) {

    public record Row(
            String tier,
            long tracked,
            long seeds) {
    }
}
