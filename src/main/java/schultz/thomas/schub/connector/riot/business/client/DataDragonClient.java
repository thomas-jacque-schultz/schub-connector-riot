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

// CDN statique : ni clé ni quota, ne passe pas par le limiteur.
@Slf4j
@Component
public class DataDragonClient {

    private static final ParameterizedTypeReference<List<String>> VERSIONS =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public DataDragonClient(@Qualifier("dataDragonRestClient") RestClient client) {
        this.client = client;
    }

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

    public String championIconUrl(String baseUrl, String version, String imageFull) {
        return baseUrl + "/cdn/" + version + "/img/champion/" + imageFull;
    }
}
