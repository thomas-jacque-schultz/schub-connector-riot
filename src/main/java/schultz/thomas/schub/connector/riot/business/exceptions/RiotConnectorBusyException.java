package schultz.thomas.schub.connector.riot.business.exceptions;

import java.time.Duration;

/**
 * Le connecteur est <strong>occupé</strong>, pas en panne : un appel interactif n'a pas obtenu
 * son créneau de quota dans le délai court qui lui est accordé.
 *
 * <p>Sous-classe de {@link RiotQuotaExceededException} pour que les chemins qui rendent déjà une
 * réponse partielle sur quota saturé la traitent sans changement. Ce qui la distingue est la
 * réponse rendue : « revenez dans un instant », jamais « le connecteur est indisponible ».</p>
 */
public class RiotConnectorBusyException extends RiotQuotaExceededException {

    public RiotConnectorBusyException(String message, Duration retryAfter) {
        super(message, retryAfter);
    }
}
