package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "L'origine de la dernière observation d'un compte.")
public enum KnownAccountSource {

    PARTICIPATION,

    RESOLUTION
}
