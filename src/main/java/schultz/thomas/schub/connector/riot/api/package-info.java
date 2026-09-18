/**
 * Couche exposée : contrôleurs HTTP et formes transportées sur le fil.
 *
 * <p>C'est la seule surface publiable. Un consommateur qui parle à ce connecteur n'a besoin de
 * connaître que ce paquet — et n'y trouve, volontairement, aucun vocabulaire Riot : ni
 * {@code match-v5}, ni {@code summonerId}, ni routage régional. Il demande les
 * <em>parties</em> d'un <em>joueur</em>.</p>
 *
 * <h2>Ce qui n'est pas ici, et ne le sera pas</h2>
 *
 * <p>Aucune notion d'équipe, aucun seuil « 4 des 5 », aucun pool jouable par poste. Ce sont des
 * jugements de domaine ; ils vivent dans {@code schub-core}, paquet {@code team}. Le jour où
 * l'un d'eux apparaît ici, le connecteur a commencé un travail qu'il ne peut pas finir.</p>
 */
package schultz.thomas.schub.connector.riot.api;
