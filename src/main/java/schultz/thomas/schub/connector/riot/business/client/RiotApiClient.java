package schultz.thomas.schub.connector.riot.business.client;

import org.bson.Document;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotAccountResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotChampionMasteryResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueEntryResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// L'API Riot, quota compris : chaque appel passe par le limiteur de la voie courante.
public interface RiotApiClient {

    Optional<RiotAccountResponse> accountByRiotId(String gameName, String tagLine);

    Optional<RiotAccountResponse> accountByPuuid(String puuid);

    // startTime en secondes depuis l'époque, contrairement au reste de l'API Riot (millisecondes).
    List<String> matchIds(String puuid, Instant startTime, int start, int count);

    // JSON non typé : ce qui n'est pas déclaré serait perdu, et Riot ne garde pas l'historique.
    Optional<Document> match(String matchId);

    // Même politique que la partie : le brut entier, faute de pouvoir le redemander un jour.
    Optional<Document> timeline(String matchId);

    List<RiotLeagueEntryResponse> leagueEntries(String puuid);

    List<String> rankedMatchIds(String puuid, int queueId, int count);

    // 205 joueurs par page avec leur puuid (vérifié le 23-09) ; au-delà de la dernière page, une liste vide.
    List<RiotLeagueEntryResponse> ladderPage(String queue, String tier, String division, int page);

    // Toute la ligue en un appel (301 Challenger le 23-09).
    List<RiotLeagueEntryResponse> apexLeague(String queue, String tier);

    List<RiotChampionMasteryResponse> masteries(String puuid);
}
