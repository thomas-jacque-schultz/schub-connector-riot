package schultz.thomas.schub.connector.riot.business.exceptions;

// Une maintenance de la base exige une file à l'arrêt : en pause, et plus aucune tâche en route.
public class IngestNotPausedException extends RuntimeException {

    public IngestNotPausedException(String message) {
        super(message);
    }
}
