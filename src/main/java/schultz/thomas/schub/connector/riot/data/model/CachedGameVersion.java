package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * « Quelle est la version courante du jeu ? » — la seule part <em>mouvante</em> de Data Dragon.
 *
 * <p>Distinguée du catalogue lui-même exprès : la question a un TTL, la réponse n'en a pas.
 * Confondre les deux mènerait soit à re-télécharger 160 ko de catalogue toutes les six heures,
 * soit à rester bloqué sur une version périmée.</p>
 */
@Document("riot_game_version")
public record CachedGameVersion(@Id String id, String version, Instant fetchedAt) {

    public static final String CURRENT = "current";
}
