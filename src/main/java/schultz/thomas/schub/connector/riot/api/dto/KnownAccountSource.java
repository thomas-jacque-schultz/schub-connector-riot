package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** D'où vient l'identité d'un compte connu — ce qui dit aussi ce qu'on peut en attendre. */
@Schema(description = "L'origine de la dernière observation d'un compte.")
public enum KnownAccountSource {

    /** Croisé comme participant d'une partie collectée. Le Riot ID est celui de ce jour-là. */
    PARTICIPATION,

    /** Confirmé par {@code account-v1} à la demande de quelqu'un. Le Riot ID était juste alors. */
    RESOLUTION
}
