package schultz.thomas.schub.connector.riot.business.exceptions;

/**
 * Aucune clé d'API n'est configurée : le connecteur est en veille.
 *
 * <p>Il continue de servir ce que son cache contient déjà — une partie terminée reste vraie
 * sans clé — mais il n'émet aucune requête sortante. Échouer franchement ici plutôt que
 * d'envoyer une requête sans clé, qui reviendrait en 401 et brouillerait le diagnostic.</p>
 */
public class RiotKeyMissingException extends RuntimeException {

    public RiotKeyMissingException(String message) {
        super(message);
    }
}
