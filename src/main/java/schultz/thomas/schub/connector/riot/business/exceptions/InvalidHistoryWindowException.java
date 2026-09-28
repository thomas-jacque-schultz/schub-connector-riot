package schultz.thomas.schub.connector.riot.business.exceptions;

public class InvalidHistoryWindowException extends RuntimeException {

    public InvalidHistoryWindowException() {
        super("Fenêtre invalide : 1 à 1000 parties, 1 à 730 jours, plancher entre 0 et le maximum de parties.");
    }
}
