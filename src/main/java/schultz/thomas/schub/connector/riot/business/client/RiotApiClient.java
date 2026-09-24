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
import schultz.thomas.schub.connector.riot.business.quota.MethodRateLimiter;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLane;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLaneContext;
import schultz.thomas.schub.connector.riot.business.quota.RiotRateLimiter;
import schultz.thomas.schub.connector.riot.config.RiotMethods;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotAccountResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotChampionMasteryResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueEntryResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueListResponse;

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
    private static final ParameterizedTypeReference<RiotLeagueListResponse> LEAGUE_LIST =
            new ParameterizedTypeReference<>() { };

    private static final String RATE_LIMIT_TYPE = "X-Rate-Limit-Type";
    private static final String APP_RATE_LIMIT = "X-App-Rate-Limit";
    private static final String METHOD_RATE_LIMIT = "X-Method-Rate-Limit";
    private static final String COUNT = "-Count";

    private final RestClient regional;
    private final RestClient platform;
    private final RiotRateLimiter rateLimiter;
    private final MethodRateLimiter methodLimiter;
    private final RiotProperties properties;

    public RiotApiClient(@Qualifier("riotRegionalClient") RestClient regional,
                         @Qualifier("riotPlatformClient") RestClient platform,
                         RiotRateLimiter rateLimiter,
                         MethodRateLimiter methodLimiter,
                         RiotProperties properties) {
        this.regional = regional;
        this.platform = platform;
        this.rateLimiter = rateLimiter;
        this.methodLimiter = methodLimiter;
        this.properties = properties;
    }

    public Optional<RiotAccountResponse> accountByRiotId(String gameName, String tagLine) {
        return call(regional, RiotMethods.ACCOUNT, "account-v1 by-riot-id", ACCOUNT, uri -> uri
                .path("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}")
                .build(gameName, tagLine));
    }

    public Optional<RiotAccountResponse> accountByPuuid(String puuid) {
        return call(regional, RiotMethods.ACCOUNT, "account-v1 by-puuid", ACCOUNT, uri -> uri
                .path("/riot/account/v1/accounts/by-puuid/{puuid}")
                .build(puuid));
    }

    // startTime en secondes depuis l'époque, contrairement au reste de l'API Riot (millisecondes).
    public List<String> matchIds(String puuid, Instant startTime, int start, int count) {
        return call(regional, RiotMethods.MATCH_IDS, "match-v5 ids", MATCH_IDS, uri -> {
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
        return call(regional, RiotMethods.MATCH, "match-v5 match", MATCH, uri -> uri
                .path("/lol/match/v5/matches/{matchId}")
                .build(matchId)).map(Document::new);
    }

    // Même politique que la partie : le brut entier, faute de pouvoir le redemander un jour.
    public Optional<Document> timeline(String matchId) {
        return call(regional, RiotMethods.TIMELINE, "match-v5 timeline", MATCH, uri -> uri
                .path("/lol/match/v5/matches/{matchId}/timeline")
                .build(matchId)).map(Document::new);
    }

    public List<RiotLeagueEntryResponse> leagueEntries(String puuid) {
        return call(platform, RiotMethods.LEAGUE_ENTRIES, "league-v4 entries", LEAGUE_ENTRIES, uri -> uri
                .path("/lol/league/v4/entries/by-puuid/{puuid}")
                .build(puuid)).orElseGet(List::of);
    }

    public List<String> rankedMatchIds(String puuid, int queueId, int count) {
        return call(regional, RiotMethods.MATCH_IDS, "match-v5 ids", MATCH_IDS, uri -> uri
                .path("/lol/match/v5/matches/by-puuid/{puuid}/ids")
                .queryParam("queue", queueId)
                .queryParam("start", 0)
                .queryParam("count", count)
                .build(puuid)).orElseGet(List::of);
    }

    // 205 joueurs par page avec leur puuid (vérifié le 23-09) ; au-delà de la dernière page, une liste vide.
    public List<RiotLeagueEntryResponse> ladderPage(String queue, String tier, String division, int page) {
        return call(platform, RiotMethods.LEAGUE_DIVISION, "league-v4 entries by division", LEAGUE_ENTRIES, uri -> uri
                .path("/lol/league/v4/entries/{queue}/{tier}/{division}")
                .queryParam("page", page)
                .build(queue, tier, division)).orElseGet(List::of);
    }

    // Toute la ligue en un appel (301 Challenger le 23-09).
    public List<RiotLeagueEntryResponse> apexLeague(String queue, String tier) {
        String ligue = switch (tier) {
            case "CHALLENGER" -> "challengerleagues";
            case "GRANDMASTER" -> "grandmasterleagues";
            case "MASTER" -> "masterleagues";
            default -> throw new IllegalArgumentException("Pas une ligue au sommet : " + tier);
        };
        return call(platform, RiotMethods.LEAGUE_APEX, "league-v4 " + ligue, LEAGUE_LIST, uri -> uri
                .path("/lol/league/v4/{ligue}/by-queue/{queue}")
                .build(ligue, queue))
                .map(liste -> liste.entries() == null ? List.<RiotLeagueEntryResponse>of() : liste.entries().stream()
                        .map(entree -> new RiotLeagueEntryResponse(entree.puuid(), queue, tier, entree.rank(),
                                entree.leaguePoints(), entree.wins(), entree.losses(), entree.hotStreak(),
                                entree.veteran(), entree.freshBlood(), entree.inactive()))
                        .toList())
                .orElseGet(List::of);
    }

    public List<RiotChampionMasteryResponse> masteries(String puuid) {
        return call(platform, RiotMethods.MASTERY, "champion-mastery-v4", MASTERIES, uri -> uri
                .path("/lol/champion-mastery/v4/champion-masteries/by-puuid/{puuid}")
                .build(puuid)).orElseGet(List::of);
    }

    // Un 429 suspend puis reprend : réessayer aussitôt aggrave, les requêtes refusées comptent dans le quota.
    private <T> Optional<T> call(RestClient client, String method, String label, ParameterizedTypeReference<T> type,
                                 Function<UriBuilder, URI> uriFunction) {
        requireKey(label);

        for (int attempt = 0; attempt <= properties.getQuota().getMaxRetriesOn429(); attempt++) {
            QuotaLane voie = QuotaLaneContext.current();
            methodLimiter.acquire(method, voie.timeout(properties.getQuota()));
            rateLimiter.acquire(voie);
            Attempt<T> result = exchange(client, method, label, type, uriFunction);

            switch (result.outcome()) {
                case OK -> {
                    return Optional.ofNullable(result.body());
                }
                case NOT_FOUND -> {
                    log.debug("{} : 404, la ressource demandée n'existe pas.", label);
                    return Optional.empty();
                }
                case RATE_LIMITED -> {
                    if (result.applicationWide()) {
                        rateLimiter.penalise(result.retryAfter());
                    } else {
                        methodLimiter.penalise(method, result.retryAfter());
                    }
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

    private <T> Attempt<T> exchange(RestClient client, String method, String label,
                                    ParameterizedTypeReference<T> type, Function<UriBuilder, URI> uriFunction) {
        try {
            return client.get()
                    .uri(uriFunction)
                    .exchange((request, response) -> {
                        HttpHeaders entetes = response.getHeaders();
                        rateLimiter.adopte(entetes.getFirst(APP_RATE_LIMIT), entetes.getFirst(APP_RATE_LIMIT + COUNT));
                        methodLimiter.adopte(method, entetes.getFirst(METHOD_RATE_LIMIT),
                                entetes.getFirst(METHOD_RATE_LIMIT + COUNT));
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return new Attempt<T>(null, Outcome.NOT_FOUND, Duration.ZERO, null, false);
                        }
                        if (status == 429) {
                            // Sans en-tête, ou « application » : c'est la clé entière qui est à bout.
                            String portee = response.getHeaders().getFirst(RATE_LIMIT_TYPE);
                            return new Attempt<T>(null, Outcome.RATE_LIMITED, retryAfter(response.getHeaders()), null,
                                    portee == null || "application".equalsIgnoreCase(portee));
                        }
                        if (status >= 400) {
                            return new Attempt<T>(null, Outcome.FAILED, Duration.ZERO,
                                    "HTTP " + status + explain(status), false);
                        }
                        return new Attempt<>(response.bodyTo(type), Outcome.OK, Duration.ZERO, null, false);
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

    private record Attempt<T>(T body, Outcome outcome, Duration retryAfter, String message, boolean applicationWide) {
    }
}
