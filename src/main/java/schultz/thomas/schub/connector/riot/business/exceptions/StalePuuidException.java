package schultz.thomas.schub.connector.riot.business.exceptions;

import lombok.Getter;

// Riot chiffre le puuid pour chaque clé : un puuid relevé avec une autre clé répond 400.
@Getter
public class StalePuuidException extends RiotApiException {

    private final String puuid;

    public StalePuuidException(String puuid, String message) {
        super(message);
        this.puuid = puuid;
    }
}
