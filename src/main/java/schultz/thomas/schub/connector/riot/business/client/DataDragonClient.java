package schultz.thomas.schub.connector.riot.business.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.data.model.riot.DataDragonChampionList;

import java.util.List;

/**
 * Data Dragon : le catalogue, les icônes, les versions.
 *
 * <p>Ni clé, ni quota — c'est un CDN statique, il ne passe donc pas par le limiteur. Confondre
 * les deux ferait payer au catalogue un quota qu'il ne consomme pas, et retarderait les appels
 * qui, eux, le consomment.</p>
 */
@Slf4j
@Component
public class DataDragonClient {

    private static final ParameterizedTypeReference<List<String>> VERSIONS =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public DataDragonClient(@Qualifier("dataDragonRestClient") RestClient client) {
        this.client = client;
    }

    /** Versions publiées, de la plus récente à la plus ancienne. */
    public List<String> versions() {
        try {
            List<String> versions = client.get()
                    .uri("/api/versions.json")
                    .retrieve()
                    .body(VERSIONS);
            return versions == null ? List.of() : versions;
        } catch (RestClientException failure) {
            throw new RiotApiException("Data Dragon : liste des versions illisible.", failure);
        }
    }

    /** Le catalogue d'une version donnée. Immuable : une fois lu, il n'a plus à être relu. */
    public DataDragonChampionList champions(String version, String locale) {
        try {
            return client.get()
                    .uri("/cdn/{version}/data/{locale}/champion.json", version, locale)
                    .retrieve()
                    .body(DataDragonChampionList.class);
        } catch (RestClientException failure) {
            throw new RiotApiException(
                    "Data Dragon : catalogue illisible pour la version " + version + ".", failure);
        }
    }

    /** L'URL d'une icône de champion, déjà versionnée. Le front n'a pas à savoir la composer. */
    public String championIconUrl(String baseUrl, String version, String imageFull) {
        return baseUrl + "/cdn/" + version + "/img/champion/" + imageFull;
    }
}
