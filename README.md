# schub-connector-riot

Connecteur Riot : il traduit l'API Riot Games en vocabulaire de domaine, **et rien d'autre**.

## Ce qu'il ne sait pas

Ce service ignore ce qu'est une équipe, ce qu'est un joueur de l'équipe, et la règle
« une partie d'équipe est une partie où au moins 4 des 5 membres ont joué ». Cette règle vit
dans le cœur (`schub-core`, paquet `team`). Ici, on demande les parties d'un `puuid` et on les
rend — sans filtrer par file, sans juger, sans agréger.

Ce qui vit ici est ce qui est propre au système externe, et n'aurait sa place nulle part
ailleurs : la clé, le routage, le quota et le cache.

## Place dans l'architecture

Voir `Schub/docs/migration-microservices.md` pour la vue d'ensemble et
`Schub/docs/evolutions-2026-09.md` §5 pour le chantier dont ce service est le socle.

| | |
|---|---|
| Paquet racine | `schultz.thomas.schub.connector.riot` |
| Image Docker | `thomasschultzschub/schub-connector-riot` |
| Port en dev | 18085 (vers 8080 dans le conteneur) |
| Base Mongo | `riot` — le cache, et rien d'autre |

## Le routage : le piège n°1

Riot expose deux familles d'hôtes. Les confondre donne des 403/404 qui ressemblent à des bugs
de code — vérifié le 18-09 sur l'API réelle.

| Hôte | Type | API |
|---|---|---|
| `europe.api.riotgames.com` | **régional** | `account-v1`, `match-v5` |
| `euw1.api.riotgames.com` | **plateforme** | `league-v4`, `champion-mastery-v4` |
| `ddragon.leagueoflegends.com` | CDN | catalogue, icônes, versions — sans clé |

C'est pourquoi les clients HTTP sont trois beans distincts, nommés par leur route : se tromper
d'hôte devient une erreur qu'on ne peut pas commettre par distraction.

## Le cache : quatre politiques, pas une

> « Ce qui a été pull un jour ne doit pas l'être une deuxième fois. »

C'est une **exigence d'architecture**, pas un réglage de performance, et elle justifie à elle
seule que ce connecteur possède une base. Le piège serait d'écrire « un cache » au singulier.

| Donnée | Nature | Politique | Où |
|---|---|---|---|
| Détail d'une partie terminée | immuable | permanent, **jamais redemandé** | `MatchDetailService` |
| Liste des ids d'un joueur | append-only | incrémental par `startTime`, **-1 h** | `MatchHistoryService` |
| Maîtrises de champions | évolue en jouant | TTL 6 h | `ChampionMasteryService` |
| Rang et LP | volatil | TTL 1 h | `RankingService` |
| Catalogue Data Dragon | immuable par version | permanent, clé = version | `ChampionCatalogService` |

**Le point à ne pas rater** : une partie peut apparaître dans l'historique avec du retard. On
repart donc du dernier relevé **moins une heure**, et on dédoublonne sur le `matchId`. Le
recouvrement coûte une lecture Mongo ; son absence coûte des parties manquantes, qu'on ne voit
jamais puisqu'on ignore qu'elles existent.

**Corollaire tenu de l'autre côté de la frontière** : le cœur ne stocke aucune partie. Les
parties brutes vivent une seule fois, ici.

## Le quota

Limites d'une clé de développement, relevées dans `X-App-Rate-Limit` de l'API réelle :
`100:120,20:1` — 20 requêtes par seconde **et** 100 par deux minutes. Les deux fenêtres sont
tenues ensemble par `RiotRateLimiter`, et c'est cette double borne qui **est** l'étalement du
premier remplissage : la fenêtre longue impose d'elle-même un appel toutes les 1,2 s en moyenne.

Une fois l'historique constitué, un rafraîchissement ne coûte que les parties nouvelles, et le
quota redevient théorique.

Sur un 429, Riot renvoie `Retry-After` (vérifié : `retry-after: 2`). Il est respecté, et la
pénalité s'applique à **tous** les appelants : le quota est global à la clé.

## Routes exposées

Toutes derrière `X-Internal-Secret`, sauf `GET /actuator/health`. Documentées par springdoc :
`/swagger-ui.html`.

| Méthode | Route | Rôle |
|---|---|---|
| `GET` | `/players?gameName=&tagLine=` | Riot ID → `puuid` (liaison de compte) |
| `GET` | `/players/{puuid}` | `puuid` → Riot ID courant |
| `GET` | `/players/{puuid}/matches?since=` | ids de parties connus depuis une date |
| `POST` | `/players/{puuid}/matches/sync` | constitue ou prolonge l'historique |
| `GET` | `/players/{puuid}/rankings` | rang et LP par file |
| `GET` | `/players/{puuid}/champion-mastery?limit=` | maîtrises |
| `GET` | `/matches/{matchId}` | détail normalisé d'une partie |
| `POST` | `/matches/by-ids` | détails en lot, bornés |
| `GET` | `/champions?version=` | catalogue, figé par version |
| `GET` | `/game-version` | version Data Dragon courante |

## Configuration

La clé vient du secret Docker `RIOT_API_KEY`, monté sous `/run/secrets/` et repris par le
configtree. **Elle n'est jamais dans le dépôt.** Clé vide = connecteur en veille : aucune
requête sortante, mais le cache déjà constitué reste servi — une partie terminée reste vraie
sans clé.

Tout le reste (fenêtres de quota, TTL, recouvrement, bornes de pagination) est dans
`application.yml`, fait pour être changé sans toucher au code : une clé de production aura
d'autres limites.

## Authentification

Tous les appels exigent l'en-tête `X-Internal-Secret`, sauf `GET /actuator/health`.

## Lancer en local

```
mvn spring-boot:run
```

En dev, le service est monté par `schub-infra-docker/Hosting/Tool/CodeInfrastructure/docker-compose.dev.yml`
avec les sources en volume : `task dev` depuis ce dossier, puis `task logs -- schub-connector-riot`.

## Tests

`mvn package` suffit. Les tests sont écrits contre des **réponses réelles** capturées le 18-09
sur l'API de production (`src/test/resources/fixtures/`), puis allégées. Aucun ne touche au
réseau : un test qui dépend d'une clé de développement qui expire en 24 h n'est pas un test.
