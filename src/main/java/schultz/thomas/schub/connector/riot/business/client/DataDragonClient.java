package schultz.thomas.schub.connector.riot.business.client;

import com.fasterxml.jackson.databind.JsonNode;
import schultz.thomas.schub.connector.riot.data.model.riot.DataDragonChampionList;

import java.util.List;

// CDN statique : ni clé ni quota, ne passe pas par le limiteur.
public interface DataDragonClient {

    List<String> versions();

    DataDragonChampionList champions(String version, String locale);

    // JSON non typé : seuls quelques champs servent, et le format d'item.json bouge d'un patch à l'autre.
    JsonNode items(String version, String locale);

    String championIconUrl(String baseUrl, String version, String imageFull);
}
