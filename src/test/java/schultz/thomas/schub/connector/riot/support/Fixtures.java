package schultz.thomas.schub.connector.riot.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Charge des réponses Riot enregistrées.
 *
 * <p>Ces fixtures sont des réponses <strong>réelles</strong>, capturées le 18-09 sur l'API de
 * production puis allégées des blocs que le connecteur ne lit pas. Un test écrit contre une
 * réponse inventée ne vérifie que l'idée qu'on se fait de l'API ; celui-ci vérifie l'API.</p>
 *
 * <p>Ils ne touchent pas au réseau : un test qui dépend d'une clé qui expire en 24 h n'est
 * pas un test.</p>
 */
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
