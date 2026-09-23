package schultz.thomas.schub.connector.riot.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import schultz.thomas.schub.connector.riot.business.exceptions.CrawlerLockedException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotConnectorBusyException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;

import java.time.Duration;

@Slf4j
@RestControllerAdvice
public class RiotExceptionHandler {

    @ExceptionHandler(RiotResourceNotFoundException.class)
    public ProblemDetail handleNotFound(RiotResourceNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("Ressource inconnue de Riot");
        return detail;
    }

    @ExceptionHandler(CrawlerLockedException.class)
    public ProblemDetail handleCrawlerLocked(CrawlerLockedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(RiotConnectorBusyException.class)
    public ResponseEntity<ProblemDetail> handleBusy(RiotConnectorBusyException exception) {
        log.info("Appel interactif renoncé, connecteur occupé : {}", exception.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage());
        detail.setTitle("Connecteur Riot occupé");
        return new ResponseEntity<>(detail, retryAfter(exception.getRetryAfter()), HttpStatus.TOO_MANY_REQUESTS);
    }

    // Retry-After répercuté tel quel : un appel refusé compte aussi dans le quota Riot.
    @ExceptionHandler(RiotQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleQuota(RiotQuotaExceededException exception) {
        log.warn("Quota Riot épuisé : {}", exception.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage());
        detail.setTitle("Quota de l'API Riot épuisé");
        return new ResponseEntity<>(detail, retryAfter(exception.getRetryAfter()), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(RiotKeyMissingException.class)
    public ProblemDetail handleMissingKey(RiotKeyMissingException exception) {
        log.error("Connecteur en veille : {}", exception.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage());
        detail.setTitle("Aucune clé d'API Riot configurée");
        return detail;
    }

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
