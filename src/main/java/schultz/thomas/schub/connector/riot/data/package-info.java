/**
 * Couche données : le cache du connecteur, et les formes brutes du système externe.
 *
 * <h2>Deux familles, et pourquoi elles sont séparées</h2>
 *
 * <ul>
 *   <li>{@code data.model.riot} — ce que Riot envoie sur le fil. Ces formes ne franchissent
 *       jamais la frontière HTTP : un appelant qui lirait du vocabulaire Riot ici serait
 *       couplé à l'API Riot, ce que tout le connecteur cherche à éviter.</li>
 *   <li>{@code data.model} — ce qui est persisté dans la base {@code riot}, une collection par
 *       politique de cache.</li>
 * </ul>
 *
 * <h2>Arbitrage : dans un connecteur, le contrat prime</h2>
 *
 * <p>Les documents persistés portent directement les types du contrat ({@code api.dto}) comme
 * charge utile : {@code CachedMatch} contient un {@code MatchDetail}, pas une troisième forme
 * de partie. Cette couche connaît donc {@code api.dto} — et rien d'autre.</p>
 *
 * <p>C'est l'application de l'arbitrage de {@code docs/migration-microservices.md} §2 bis :
 * dans un connecteur, il n'y a pas deux types, parce que le métier du service <em>est</em> la
 * traduction. Y ajouter une forme persistée distincte donnerait trois représentations de la
 * même partie — brute, stockée, exposée — et deux mappers à tenir synchronisés, précisément ce
 * que cet arbitrage écarte. La règle de direction qui reste, et qui est tenue ici, est que
 * <strong>rien ne connaît les contrôleurs</strong>.</p>
 *
 * <p>Conséquence assumée : changer le contrat change la forme stockée. Pour les caches à TTL
 * c'est sans effet, ils se remplissent à nouveau. Pour les parties, immuables et jamais
 * redemandées, une évolution incompatible du contrat demanderait une migration — c'est le prix
 * à payer, et il est plus faible que celui d'un troisième modèle entretenu à la main.</p>
 */
package schultz.thomas.schub.connector.riot.data;
