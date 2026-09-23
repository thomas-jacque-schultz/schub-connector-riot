package schultz.thomas.schub.connector.riot.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// Réponses réelles capturées le 18-09 sur l'API de production, allégées des blocs non lus. Ne pas en inventer.
public final class Fixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Fixtures() {
    }

    public static <T> T load(String name, Class<T> type) {
        try (InputStream stream = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture absente : " + name);
            }
            return MAPPER.readValue(stream, type);
        } catch (IOException failure) {
            throw new IllegalStateException("Fixture illisible : " + name, failure);
        }
    }

    public static org.bson.Document document(String name) {
        return org.bson.Document.parse(raw(name));
    }

    public static String raw(String name) {
        try (InputStream stream = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture absente : " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Fixture illisible : " + name, failure);
        }
    }
}
