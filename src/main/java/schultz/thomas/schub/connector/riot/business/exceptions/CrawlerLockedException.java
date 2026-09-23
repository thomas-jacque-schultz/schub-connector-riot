package schultz.thomas.schub.connector.riot.business.exceptions;

public class CrawlerLockedException extends RuntimeException {

    public CrawlerLockedException() {
        super("La collecte de fond ne se bascule pas dans cet environnement");
    }
}
