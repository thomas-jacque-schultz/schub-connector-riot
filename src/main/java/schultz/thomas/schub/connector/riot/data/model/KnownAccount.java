package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;

import java.time.Instant;

/**
 * L'index des comptes qu'on sait exister : un document par {@code puuid}.
 *
 * <h2>Pourquoi cette collection</h2>
 *
 * <p>Chercher dans {@code riot_participation} ne fait connaître que les joueurs déjà croisés en
 * partie, et résoudre un Riot ID ne laissait aucune trace : un compte qu'on vient de faire
 * confirmer par Riot n'apparaissait donc nulle part. Les deux sources écrivent ici, et la
 * recherche ne lit que cette collection.</p>
 *
 * <h2>Ce qu'elle ne contient pas</h2>
 *
 * <p>Aucun chiffre de jeu — parties vues, postes, dernière partie. Ils restent calculés depuis
 * {@code riot_participation}, qui en est la seule vérité. Ici ne vit que l'identité, et elle est
 * reconstructible : voir {@code POST /ingest/known-accounts/rebuild}.</p>
 *
 * @param observedAt date de l'observation qui a écrit cette identité — la partie pour une
 *                   participation, l'appel pour une résolution. Une écriture n'a lieu que si elle
 *                   est plus récente que celle en place, ce qui rend l'ordre des observations
 *                   sans importance.
 * @param searchName {@code gameName} replié : Mongo n'ignore pas les accents dans une
 *                   {@code $regex}, le repli est donc fait à l'écriture pour que la recherche
 *                   porte sur un index.
 */
@Document("riot_known_account")
@CompoundIndex(name = "searchName_observedAt", def = "{'searchName': 1, 'observedAt': -1}")
public record KnownAccount(
        @Id String puuid,
        String gameName,
        String tagLine,
        @Indexed String searchName,
        Instant observedAt,
        KnownAccountSource source
) {

    public String riotId() {
        if (gameName == null || gameName.isBlank()) {
            return null;
        }
        return tagLine == null || tagLine.isBlank() ? gameName : gameName + "#" + tagLine;
    }
}
