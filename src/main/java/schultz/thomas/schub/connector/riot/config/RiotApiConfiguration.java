package schultz.thomas.schub.connector.riot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Les trois clients HTTP du connecteur, un par hôte.
 *
 * <p>Trois beans distincts plutôt qu'un seul avec des URL absolues : c'est le seul moyen de
 * rendre le routage <em>impossible à confondre</em> au point d'appel. Un service qui injecte
 * {@code riotRegionalClient} ne peut pas se tromper d'hôte par distraction.</p>
 *
 * <p>La clé est posée ici, une fois, sur les deux clients Riot. Data Dragon n'en demande pas —
 * c'est un CDN statique, et lui envoyer la clé la ferait fuiter sans contrepartie.</p>
 *
 * <p>Conséquence à connaître : la clé est figée à la construction des beans. En changer demande
 * un redémarrage du service — c'est le même contrat que le jeton d'appairage du connecteur
 * Freebox, et c'est acceptable pour un secret qu'on ne fait pas tourner à chaud.</p>
 */
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(RiotProperties.class)
public class RiotApiConfiguration {

    public static final String API_KEY_HEADER = "X-Riot-Token";

    private final RiotProperties properties;

    /** {@code account-v1}, {@code match-v5}. */
    @Bean("riotRegionalClient")
    public RestClient riotRegionalClient() {
        return riotDefaults(RestClient.builder(), properties.regionalBaseUrl()).build();
    }

    /** {@code league-v4}, {@code champion-mastery-v4}. */
    @Bean("riotPlatformClient")
    public RestClient riotPlatformClient() {
        return riotDefaults(RestClient.builder(), properties.platformBaseUrl()).build();
    }

    /** Catalogue des champions, icônes, versions. Aucune clé, aucun quota. */
    @Bean("dataDragonRestClient")
    public RestClient dataDragonRestClient() {
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(timeouts()))
                .baseUrl(properties.getDataDragonBaseUrl())
                .build();
    }

    /**
     * Hôte, clé et bornes de temps : tout ce qui fait un client Riot.
     *
     * <p>Exposée plutôt qu'enfouie dans les méthodes de bean pour que les tests montent leurs
     * clients par ce chemin-là. Sans cela, ils vérifieraient un câblage qu'ils auraient eux-mêmes
     * reconstitué — et la seule chose qui compte ici, l'en-tête de clé et le bon hôte, ne serait
     * jamais réellement couverte.</p>
     */
    public RestClient.Builder riotDefaults(RestClient.Builder builder, String baseUrl) {
        return builder
                .requestFactory(ClientHttpRequestFactories.get(timeouts()))
                .baseUrl(baseUrl)
                .defaultHeader(API_KEY_HEADER, properties.getApiKey());
    }

    /** Sans bornes explicites, une API qui ne répond plus fait pendre l'appelant. */
    private ClientHttpRequestFactorySettings timeouts() {
        return ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
    }
}
