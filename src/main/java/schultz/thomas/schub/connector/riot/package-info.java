/**
 * Connecteur Riot : il traduit l'API Riot Games en vocabulaire de domaine, et rien d'autre.
 *
 * <h2>Un connecteur ne sait pas pourquoi on l'appelle</h2>
 *
 * <p>Ce service ignore ce qu'est une équipe, ce qu'est un joueur de l'équipe, et la règle
 * « une partie d'équipe est une partie où au moins 4 des 5 membres ont joué ». Cette règle
 * appartient au cœur ({@code schub-core}, paquet {@code team}). Ici, on demande les parties
 * d'un {@code puuid} et on les rend — sans filtrer par file, sans juger, sans agréger.</p>
 *
 * <p>Ce qui vit ici est ce qui est propre au système externe, et qui n'aurait sa place nulle
 * part ailleurs : la clé, le routage, le quota et le cache.</p>
 *
 * <h2>Les deux routages</h2>
 *
 * <pre>
 *   europe.api.riotgames.com   (régional)     account-v1, match-v5
 *   euw1.api.riotgames.com     (plateforme)   league-v4, champion-mastery-v4
 *   ddragon.leagueoflegends.com               catalogue, icônes, versions — sans clé
 * </pre>
 *
 * <p>Les confondre donne des 403/404 qui ressemblent à des bugs de code. C'est pourquoi les
 * clients HTTP sont trois beans distincts, nommés par leur route : se tromper d'hôte devient
 * une erreur qu'on ne peut pas commettre par distraction.</p>
 *
 * <h2>Le cache : quatre politiques, pas une</h2>
 *
 * <p>« Ce qui a été pull un jour ne doit pas l'être une deuxième fois » est une exigence
 * d'architecture, pas un réglage de performance — et elle justifie à elle seule que ce
 * connecteur possède une base. Le piège serait d'écrire « un cache » au singulier :</p>
 *
 * <pre>
 *   détail d'une partie terminée   immuable              permanent, jamais redemandé
 *   liste des ids d'un joueur      append-only           incrémental par startTime, -1 h
 *   maîtrises de champions         évolue en jouant      TTL 6 h
 *   rang et LP                     volatil               TTL 1 h
 *   catalogue Data Dragon          immuable par version  permanent, clé = version
 * </pre>
 *
 * <p>Corollaire tenu de l'autre côté de la frontière : <strong>le cœur ne stocke aucune
 * partie</strong>. Les parties brutes vivent une seule fois, ici. Deux copies de la même
 * donnée, c'est deux vérités et une divergence garantie.</p>
 *
 * <h2>Les trois couches, et le sens des flèches</h2>
 *
 * <pre>
 *   api  ---&gt;  business  ---&gt;  data
 * </pre>
 *
 * <ul>
 *   <li>{@code api} — contrôleurs et formes transportées sur le fil ({@code dto}). C'est la
 *       seule surface publiable : un consommateur qui parle à ce service n'a besoin de
 *       connaître que ce paquet.</li>
 *   <li>{@code business} — les services, la traduction, le quota. Il connaît {@code data} et
 *       les DTO, <strong>jamais les contrôleurs</strong>.</li>
 *   <li>{@code data} — ce qui est persisté et les formes brutes du système externe. Sur
 *       l'arbitrage « dans un connecteur, le contrat prime », voir son {@code package-info}.</li>
 *   <li>{@code config} — transverse, à la racine : il câble les trois couches, donc il les voit
 *       toutes. C'est le seul paquet autorisé à le faire.</li>
 * </ul>
 */
package schultz.thomas.schub.connector.riot;
