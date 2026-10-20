package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;

import java.util.List;

// Répartition d'une valeur par partie, par poste et tous paliers confondus (Schub#129) ; la dernière case prend le reste.
public record DistributionMetric(String key, Object value, double step, int buckets) {

    public static final List<DistributionMetric> CATALOGUE = List.of(
            new DistributionMetric("kills", "$kills", 1, 21),
            new DistributionMetric("deaths", "$deaths", 1, 21),
            new DistributionMetric("kda", new Document("$divide", List.of(
                    new Document("$add", List.of("$kills", "$assists")),
                    new Document("$max", List.of("$deaths", 1)))), 0.5, 21));
}
