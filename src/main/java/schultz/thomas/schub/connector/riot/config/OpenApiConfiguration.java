package schultz.thomas.schub.connector.riot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenApiConfiguration {

    private static final String SECRET_SCHEME = "internalSecret";

    // Jackson écrit une Duration en ISO-8601 (« PT2H36M20S ») ; sans cela le contrat la décrirait comme un objet.
    static {
        SpringDocUtils.getConfig().replaceWithSchema(Duration.class,
                new StringSchema().format("duration").example("PT2H36M20S"));
    }

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
