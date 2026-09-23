package schultz.thomas.schub.connector.riot.business.client;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLaneContext;
import schultz.thomas.schub.connector.riot.business.quota.RiotRateLimiter;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotAccountResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotChampionMasteryResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueEntryResponse;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// account-v1 et match-v5 : route régionale (europe). league-v4 et champion-mastery-v4 : plateforme (euw1).
// Mauvais hôte = 403, qui ressemble à une clé invalide (vérifié le 18-09).
@Slf4j
@Component
public class RiotApiClient {

    private static final ParameterizedTypeReference<RiotAccountResponse> ACCOUNT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<String>> MATCH_IDS =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Map<String, Object>> MATCH =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<RiotLeagueEntryResponse>> LEAGUE_ENTRIES =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<RiotChampionMasteryResponse>> MASTERIES =
            new ParameterizedTypeReference<>() { };

    private final RestClient regional;
    private final RestClient platform;
    private final RiotRateLimiter rateLimiter;
    private final RiotProperties properties;

    public RiotApiClient(@Qualifier("riotRegionalClient") RestClient regional,
                         @Qualifier("riotPlatformClient") RestClient platform,
                         RiotRateLimiter rateLimiter,
                         RiotProperties properties) {
        this.regional = regional;
        this.platform = platform;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public Optional<RiotAccountResponse> accountByRiotId(String gameName, String tagLine) {
        return call(regional, "account-v1 by-riot-id", ACCOUNT, uri -> uri
                .path("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}")
                .build(gameName, tagLine));
    }

    public Optional<RiotAccountResponse> accountByPuuid(String puuid) {
        return call(regional, "account-v1 by-puuid", ACCOUNT, uri -> uri
                .path("/riot/account/v1/accounts/by-puuid/{puuid}")
                .build(puuid));
    }

    // startTime en secondes depuis l'époque, contrairement au reste de l'API Riot (millisecondes).
    public List<String> matchIds(String puuid, Instant startTime, int start, int count) {
        return call(regional, "match-v5 ids", MATCH_IDS, uri -> {
            uri.path("/lol/match/v5/matches/by-puuid/{puuid}/ids")
                    .queryParam("start", start)
                    .queryParam("count", count);
            if (startTime != null) {
                uri.queryParam("startTime", startTime.getEpochSecond());
            }
            return uri.build(puuid);
        }).orElseGet(List::of);
    }

    // JSON non typé : ce qui n'est pas déclaré serait perdu, et Riot ne garde pas l'historique.
    public Optional<Document> match(String matchId) {
        return call(regional, "match-v5 match", MATCH, uri -> uri
                .path("/lol/match/v5/matches/{matchId}")
                .build(matchId)).map(Document::new);
    }

    public List<RiotLeagueEntryResponse> leagueEntries(String puuid) {
        return call(platform, "league-v4 entries", LEAGUE_ENTRIES, uri -> uri
                .path("/lol/league/v4/entries/by-puuid/{puuid}")
                .build(puuid)).orElseGet(List::of);
    }

    public List<RiotChampionMasteryResponse> masteries(String puuid) {
        return call(platform, "champion-mastery-v4", MASTERIES, uri -> uri
                .path("/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}")
                .build(puuid)).orElseGet(List::of);
    }

    // Un 429 suspend puis reprend : réessayer aussitôt aggrave, les requêtes refusées comptent dans le quota.
    private <T> Optional<T> call(RestClient client, String label, ParameterizedTypeReference<T> type,
                                 Function<UriBuilder, URI> uriFunction) {
        requireKey(label);

        for (int attempt = 0; attempt <= properties.getQuota().getMaxRetriesOn429(); attempt++) {
            rateLimiter.acquire(QuotaLaneContext.current());
            Attempt<T> result = exchange(client, label, type, uriFunction);

            switch (result.outcome()) {
                case OK -> {
                    return Optional.ofNullable(result.body());
                }
                case NOT_FOUND -> {
                    log.debug("{} : 404, la ressource demandée n'existe pas.", label);
                    return Optional.empty();
                }
                case RATE_LIMITED -> {
                    rateLimiter.penalise(result.retryAfter());
                    log.info("{} : 429, reprise dans {} (tentative {}).", label, result.retryAfter(), attempt + 1);
                }
                case FAILED -> throw new RiotApiException(label + " : " + result.message());
                default -> throw new IllegalStateException("Issue d'appel non couverte.");
            }
        }

        throw new RiotQuotaExceededException(
                label + " : toujours refusé après " + properties.getQuota().getMaxRetriesOn429()
                        + " reprises sur 429.",
                properties.getQuota().getBurstWindow());
    }

    private <T> Attempt<T> exchange(RestClient client, String label, ParameterizedTypeReference<T> type,
                                    Function<UriBuilder, URI> uriFunction) {
        try {
            return client.get()
                    .uri(uriFunction)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return new Attempt<T>(null, Outcome.NOT_FOUND, Duration.ZERO, null);
                        }
                        if (status == 429) {
                            return new Attempt<T>(null, Outcome.RATE_LIMITED, retryAfter(response.getHeaders()), null);
                        }
                        if (status >= 400) {
                            return new Attempt<T>(null, Outcome.FAILED, Duration.ZERO,
                                    "HTTP " + status + explain(status));
                        }
                        return new Attempt<>(response.bodyTo(type), Outcome.OK, Duration.ZERO, null);
                    });
        } catch (RuntimeException failure) {
            throw new RiotApiException(label + " : appel impossible — " + failure.getMessage(), failure);
        }
    }

    // Retry-After en secondes (vérifié : retry-after: 2).
    private Duration retryAfter(HttpHeaders headers) {
        String header = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (header != null) {
            try {
                return Duration.ofSeconds(Long.parseLong(header.trim()));
            } catch (NumberFormatException ignored) {
                log.warn("Retry-After illisible : « {} ». Repli sur la fenêtre courte.", header);
            }
        }
        return properties.getQuota().getBurstWindow();
    }

    private String explain(int status) {
        return switch (status) {
            case 401, 403 -> " — clé absente, expirée, ou mauvais routage (régional vs plateforme).";
            case 503 -> " — l'API Riot est indisponible.";
            default -> "";
        };
    }

    private void requireKey(String label) {
        if (!properties.hasKey()) {
            throw new RiotKeyMissingException(
                    "Aucune clé d'API Riot configurée : " + label + " n'est pas émis. "
                            + "Le cache déjà constitué reste servi.");
        }
    }

    private enum Outcome { OK, NOT_FOUND, RATE_LIMITED, FAILED }

    private record Attempt<T>(T body, Outcome outcome, Duration retryAfter, String message) {
    }
}
