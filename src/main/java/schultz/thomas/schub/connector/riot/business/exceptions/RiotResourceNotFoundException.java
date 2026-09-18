package schultz.thomas.schub.connector.riot.business.exceptions;

/** Riot a répondu 404 : ce Riot ID, ce puuid ou cette partie n'existe pas (ou plus). */
public class RiotResourceNotFoundException extends RuntimeException {

    public RiotResourceNotFoundException(String message) {
        super(message);
    }
}
