# schub-connector-riot

Connecteur Riot : appels à l'API Riot Games.

## Place dans l'architecture

Voir `Schub/docs/migration-microservices.md` pour la vue d'ensemble.

| | |
|---|---|
| Paquet racine | `schultz.thomas.schub.connector.riot` |
| Image Docker | `thomasschultzschub/schub-connector-riot` |
| Port en dev | 18085 (vers 8080 dans le conteneur) |
| Base Mongo | aucune — ce service est sans état |

## Authentification

Tous les appels exigent l'en-tête `X-Internal-Secret`, sauf `GET /actuator/health`.

## Lancer en local

```
mvn spring-boot:run
```

En dev, le service est monté par `schub-infra-docker/Hosting/Tool/CodeInfrastructure/docker-compose.dev.yml`
avec les sources en volume : `task dev` depuis ce dossier, puis `task logs -- schub-connector-riot`.
