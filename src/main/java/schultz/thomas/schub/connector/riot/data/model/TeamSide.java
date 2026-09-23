package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

// Un camp d'une partie à timeline, à 15 minutes. Réécrit à chaque recalcul du début de partie.
@Document(TeamSide.COLLECTION)
@CompoundIndex(name = "patch_queueId", def = "{'patch': 1, 'queueId': 1}")
public record TeamSide(
        @Id String id,
        String matchId,
        int side,
        String patch,
        int queueId,
        // Palier moyen des cinq, regroupé comme les référentiels (Maître+). Absent : aucun rang connu.
        String tier,
        boolean win,
        Integer goldDiffAt15,
        Integer xpDiffAt15,
        Integer killsDiffAt15,
        int dragons,
        int grubs,
        int heralds,
        int ganksDecisive,
        int ganksConceded
) {

    public static final String COLLECTION = "riot_team_side";

    public static String idOf(String matchId, int side) {
        return matchId + "#" + side;
    }
}
