/**
 * Couche métier : la traduction, le quota et les politiques de cache.
 *
 * <p>Elle connaît {@code data} et le contrat {@code api.dto} — jamais les contrôleurs.</p>
 *
 * <h2>Ce qui vit ici, et ce qui n'y vit pas</h2>
 *
 * <p>Ce connecteur ne sait pas <em>pourquoi</em> on l'appelle. Il ignore ce qu'est une équipe,
 * ce qu'est un joueur de l'équipe, et la règle « une partie d'équipe est une partie où au moins
 * 4 des 5 membres ont joué » : elle appartient au cœur. Ce qui vit ici est ce qui est propre au
 * système externe — le routage régional/plateforme, la clé, le quota, et le fait qu'une partie
 * terminée ne change plus jamais.</p>
 *
 * <h2>Les quatre politiques de cache, et où elles sont</h2>
 *
 * <ul>
 *   <li>{@code MatchDetailService} — partie terminée : <strong>permanente</strong>, jamais
 *       redemandée.</li>
 *   <li>{@code MatchHistoryService} — liste d'ids : <strong>incrémentale</strong> par
 *       {@code startTime}, avec recouvrement d'une heure et dédoublonnage sur le
 *       {@code matchId}.</li>
 *   <li>{@code ChampionMasteryService} — <strong>TTL 6 h</strong>.</li>
 *   <li>{@code RankingService} — <strong>TTL 1 h</strong>.</li>
 *   <li>{@code ChampionCatalogService} — <strong>permanent par version</strong> ; seule la
 *       question « quelle est la version courante ? » porte un TTL.</li>
 * </ul>
 */
package schultz.thomas.schub.connector.riot.business;
