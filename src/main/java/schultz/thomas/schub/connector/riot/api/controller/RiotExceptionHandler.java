package schultz.thomas.schub.connector.riot.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotConnectorBusyException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;

import java.time.Duration;

/**
 * Traduit un échec de dialogue avec Riot en réponse HTTP honnête.
 *
 * <p>Quatre statuts distincts et non un 500 général : l'appelant doit pouvoir distinguer
 * « ce joueur n'existe pas » de « le quota est épuisé » et de « Riot est en panne ». Les trois
 * appellent des conduites différentes, et les confondre ferait réessayer là où il ne faut pas.</p>
 */
@Slf4j
@RestControllerAdvice
public class RiotExceptionHandler {

    @ExceptionHandler(RiotResourceNotFoundException.class)
    public ProblemDetail handleNotFound(RiotResourceNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("Ressource inconnue de Riot");
        return detail;
    }

    /**
     * Occupé, pas en panne.
     *
     * <p>429 et non 503 : le connecteur répond, sa clé est valide, et le créneau manquant est
     * affaire de secondes. Le taire derrière une expiration réseau était la moitié du défaut
     * corrigé le 21-09 — l'appelant concluait « indisponible » et conservait un lien non
     * résolu.</p>
     */
    @ExceptionHandler(RiotConnectorBusyException.class)
    public ResponseEntity<ProblemDetail> handleBusy(RiotConnectorBusyException exception) {
        log.info("Appel interactif renoncé, connecteur occupé : {}", exception.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage());
        detail.setTitle("Connecteur Riot occupé");
        return new ResponseEntity<>(detail, retryAfter(exception.getRetryAfter()), HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Le {@code Retry-After} est répercuté tel que Riot l'a donné.
     *
     * <p>Le taire ferait réessayer immédiatement, ce qui empire la situation : les requêtes
     * refusées comptent elles aussi dans le quota.</p>
     */
    @ExceptionHandler(RiotQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleQuota(RiotQuotaExceededException exception) {
        log.warn("Quota Riot épuisé : {}", exception.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage());
        detail.setTitle("Quota de l'API Riot épuisé");
        return new ResponseEntity<>(detail, retryAfter(exception.getRetryAfter()), HttpStatus.TOO_MANY_REQUESTS);
    }

    /** Pas une panne : une configuration absente. 503 le dit, 500 le cacherait. */
    @ExceptionHandler(RiotKeyMissingException.class)
    public ProblemDetail handleMissingKey(RiotKeyMissingException exception) {
        log.error("Connecteur en veille : {}", exception.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage());
        detail.setTitle("Aucune clé d'API Riot configurée");
        return detail;
    }

    /** 502 plutôt que 500 : la panne est en amont, et l'appelant doit pouvoir le distinguer. */
    @ExceptionHandler(RiotApiException.class)
    public ProblemDetail handleUpstreamFailure(RiotApiException exception) {
        log.warn("Échec du dialogue avec l'API Riot : {}", exception.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                exception.getMessage());
        detail.setTitle("L'API Riot n'a pas pu être jointe");
        return detail;
    }

    private HttpHeaders retryAfter(Duration delay) {
        HttpHeaders headers = new HttpHeaders();
        if (delay != null && !delay.isZero()) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, delay.toSeconds())));
        }
        return headers;
    }
}
