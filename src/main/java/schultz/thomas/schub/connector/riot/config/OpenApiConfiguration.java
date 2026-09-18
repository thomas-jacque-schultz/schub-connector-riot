package schultz.thomas.schub.connector.riot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentation de l'API interne : toutes les routes sont derrière {@code X-Internal-Secret}. */
@Configuration
public class OpenApiConfiguration {

    private static final String SECRET_SCHEME = "internalSecret";

    @Bean
    public OpenAPI riotConnectorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Connecteur Riot")
                        .version("0.1.0")
                        .description("""
                                Traduit l'API Riot Games en vocabulaire de domaine, et rien d'autre.
                                Ce service ne connaît ni la notion d'équipe, ni la règle « au moins
                                4 des 5 membres » : elles vivent dans le cœur.

                                Il détient en revanche ce qui est propre au système externe : la clé,
                                le routage régional/plateforme, le quota et les quatre politiques de
                                cache."""))
                .components(new Components().addSecuritySchemes(SECRET_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Secret")
                                .description("Secret partagé par tous les services Schub.")))
                .addSecurityItem(new SecurityRequirement().addList(SECRET_SCHEME));
    }
}
