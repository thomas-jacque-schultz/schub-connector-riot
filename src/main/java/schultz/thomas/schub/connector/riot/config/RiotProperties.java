package schultz.thomas.schub.connector.riot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Comment joindre l'API Riot, et selon quelles politiques de cache.
 *
 * <p>Ce connecteur ne sait pas <em>pourquoi</em> on l'appelle : il ne connaît ni la notion
 * d'équipe, ni la règle « au moins 4 des 5 membres ». Il ne détient que ce qui est propre au
 * système externe — la clé, le routage, le quota et le cache.</p>
 *
 * <h2>Les deux routages, et le piège</h2>
 *
 * <p>Riot expose deux familles d'hôtes, et les confondre donne des 403/404 qui ressemblent à
 * des bugs de code :</p>
 * <ul>
 *   <li><strong>régional</strong> ({@code europe.api.riotgames.com}) — {@code account-v1} et
 *       {@code match-v5} ;</li>
 *   <li><strong>plateforme</strong> ({@code euw1.api.riotgames.com}) — {@code league-v4} et
 *       {@code champion-mastery-v4}.</li>
 * </ul>
 *
 * <p>Vérifié sur l'API réelle le 18-09 : {@code account-v1} appelé sur {@code euw1} répond 403,
 * et {@code match-v5} appelé sur {@code euw1} répond 403 lui aussi.</p>
 */
@Data
@ConfigurationProperties(prefix = "riot")
public class RiotProperties {

    /**
     * Clé d'API. Fournie par secret Docker (configtree) ou par {@code RIOT_API_KEY} ;
     * jamais en clair dans le dépôt, jamais journalisée.
     *
     * <p>Vide = connecteur en veille : aucune requête n'est émise vers Riot, les lectures
     * servent uniquement ce que le cache contient déjà.</p>
     */
    private String apiKey = "";

    /** Route régionale : {@code account-v1} et {@code match-v5}. */
    private String region = "europe";

    /** Route de plateforme : {@code league-v4} et {@code champion-mastery-v4}. */
    private String platform = "euw1";

    private String dataDragonBaseUrl = "https://ddragon.leagueoflegends.com";

    /** Langue du catalogue Data Dragon. Le reste de Schub est en français. */
    private String dataDragonLocale = "fr_FR";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(10);

    private final Quota quota = new Quota();

    private final Cache cache = new Cache();

    private final Ingest ingest = new Ingest();

    public String regionalBaseUrl() {
        return "https://" + region + ".api.riotgames.com";
    }

    public String platformBaseUrl() {
        return "https://" + platform + ".api.riotgames.com";
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Les deux fenêtres d'une clé de développement, relevées dans l'en-tête
     * {@code X-App-Rate-Limit} de l'API réelle : {@code 100:120,20:1}.
     *
     * <p>Ces deux fenêtres <em>sont</em> l'étalement du premier remplissage : la seconde borne
     * le pic, la première impose un régime moyen d'environ un appel toutes les 1,2 s. Une fois
     * l'historique constitué, un rafraîchissement ne coûte que les parties nouvelles et le
     * quota redevient théorique.</p>
     */
    @Data
    public static class Quota {

        private int burstRequests = 20;

        private Duration burstWindow = Duration.ofSeconds(1);

        private int sustainedRequests = 100;

        private Duration sustainedWindow = Duration.ofMinutes(2);

        /**
         * Marge de sécurité retirée de chaque fenêtre. Riot compte les requêtes à sa façon et
         * son horloge n'est pas la nôtre : viser exactement la limite, c'est la dépasser.
         */
        private int safetyMargin = 2;

        /** Nombre de reprises après un 429. Au-delà, l'appel remonte en erreur. */
        private int maxRetriesOn429 = 3;

        /** Plafond de ce qu'on accepte d'attendre sur un {@code Retry-After} aberrant. */
        private Duration maxRetryAfter = Duration.ofMinutes(3);

        /** Attente maximale d'un créneau sur la voie de collecte, qui a tout son temps. */
        private Duration acquireTimeout = Duration.ofMinutes(5);

        /**
         * Attente maximale d'un créneau sur la voie interactive.
         *
         * <p><strong>Doit rester nettement sous le {@code read-timeout} du cœur</strong>
         * ({@code connector.riot.read-timeout}, 5 s) : le budget restant paie l'appel à Riot
         * lui-même. Au-delà, le cœur voit une expiration réseau et conclut « connecteur
         * indisponible » là où il est seulement occupé.</p>
         */
        private Duration interactiveTimeout = Duration.ofSeconds(2);

        /**
         * Durée au-delà de laquelle une tâche de collecte cesse de céder le passage aux appels
         * interactifs. C'est la borne qui l'empêche d'être affamée par un flux interactif
         * soutenu : elle garantit au moins un créneau par {@code bulk-yield}.
         */
        private Duration bulkYield = Duration.ofSeconds(10);

        /**
         * Créneaux de la fenêtre soutenue que la collecte ne prend jamais.
         *
         * <p>Céder du temps ne suffit pas : la collecte consomme les 98 créneaux en une vingtaine
         * de secondes, et pendant les cent suivantes il n'y a plus rien à céder. La réserve est
         * une part du même compteur, jamais un supplément.</p>
         */
        private int interactiveReserve = 10;

        /**
         * Sans demande interactive depuis ce délai, la collecte reprend la réserve — à un créneau
         * près, celui qui garantit que la demande suivante soit servie tout de suite et réarme
         * le reste.
         */
        private Duration interactiveReserveIdle = Duration.ofMinutes(1);
    }

    /**
     * La file d'ingestion. Elle existe parce qu'un premier remplissage dure des heures :
     * mille parties par joueur au plus (mesuré), un appel chacune, environ 49 par minute.
     */
    @Data
    public static class Ingest {

        private boolean enabled = true;

        private Duration pollInterval = Duration.ofSeconds(2);

        /** Tâches traitées au plus par tour, pour que la boucle rende la main régulièrement. */
        private int batchSize = 25;

        /**
         * Bail d'une tâche réclamée. <strong>Doit dépasser {@code quota.acquireTimeout}</strong> :
         * une tâche qui attend légitimement son tour de quota se ferait sinon voler par le
         * rattrapage des tâches orphelines.
         */
        private Duration lease = Duration.ofMinutes(15);

        private int maxAttempts = 5;

        private Duration retryBackoff = Duration.ofMinutes(1);

        /** Repos après un quota saturé, avant de rendre la main à la file. */
        private Duration quotaBackoff = Duration.ofSeconds(30);

        /** Repos quand aucune clé n'est configurée : inutile de tourner à vide. */
        private Duration idleBackoff = Duration.ofMinutes(5);
    }

    /**
     * Quatre natures de données, quatre politiques. Les confondre donne soit du gaspillage de
     * quota, soit des chiffres faux (docs/evolutions-2026-09.md, D.2 ter).
     */
    @Data
    public static class Cache {

        /**
         * Recouvrement appliqué au curseur d'historique : on repart du dernier relevé
         * <strong>moins</strong> cette durée.
         *
         * <p>Une partie peut apparaître dans l'historique avec du retard. Le recouvrement coûte
         * une lecture Mongo ; son absence coûte des parties manquantes, qu'on ne voit jamais
         * puisqu'on ignore qu'elles existent.</p>
         */
        private Duration historyOverlap = Duration.ofHours(1);

        /**
         * En deçà de cette fraîcheur, une lecture d'historique se sert du cache sans rappeler
         * Riot. C'est ce qui évite qu'afficher une page d'équipe déclenche cinq synchronisations.
         */
        private Duration historyFreshness = Duration.ofMinutes(15);

        /** Point de départ d'un premier remplissage, faute de curseur. */
        private Duration historyDepth = Duration.ofDays(365);

        /** Rang et LP : volatil. Afficher un LP d'hier serait un bug visible. */
        private Duration rankingTtl = Duration.ofHours(1);

        /** Maîtrises : un pool de champions n'a pas besoin d'être à la seconde. */
        private Duration masteryTtl = Duration.ofHours(6);

        /**
         * Durée de validité de la <em>résolution</em> « quelle est la version courante ».
         * Le catalogue d'une version donnée, lui, est immuable et gardé pour toujours.
         */
        private Duration gameVersionTtl = Duration.ofHours(6);

        /** Nombre d'ids demandés par page à {@code match-v5} (maximum autorisé : 100). */
        private int idPageSize = 100;

        /** Garde-fou : nombre de pages d'ids au maximum par synchronisation. */
        private int maxIdPages = 50;

        /**
         * Nombre de détails de parties récupérés au plus par appel entrant.
         *
         * <p>Sans cette borne, une première synchronisation à mille parties ferait pendre la
         * requête HTTP du cœur pendant vingt minutes. Ce qui dépasse est rendu comme
         * « restant à récupérer » et le prochain appel reprend où celui-ci s'est arrêté.</p>
         */
        private int maxDetailsPerCall = 60;
    }
}
