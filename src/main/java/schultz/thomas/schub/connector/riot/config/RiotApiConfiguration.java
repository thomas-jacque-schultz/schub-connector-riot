package schultz.thomas.schub.connector.riot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(RiotProperties.class)
public class RiotApiConfiguration {

    public static final String API_KEY_HEADER = "X-Riot-Token";

    private final RiotProperties properties;

    @Bean("riotRegionalClient")
    public RestClient riotRegionalClient() {
        return riotDefaults(RestClient.builder(), properties.regionalBaseUrl()).build();
    }

    @Bean("riotPlatformClient")
    public RestClient riotPlatformClient() {
        return riotDefaults(RestClient.builder(), properties.platformBaseUrl()).build();
    }

    @Bean("dataDragonRestClient")
    public RestClient dataDragonRestClient() {
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(timeouts()))
                .baseUrl(properties.getDataDragonBaseUrl())
                .build();
    }

    public RestClient.Builder riotDefaults(RestClient.Builder builder, String baseUrl) {
        return builder
                .requestFactory(ClientHttpRequestFactories.get(timeouts()))
                .baseUrl(baseUrl)
                .defaultHeader(API_KEY_HEADER, properties.getApiKey());
    }

    private ClientHttpRequestFactorySettings timeouts() {
        return ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
    }
}
