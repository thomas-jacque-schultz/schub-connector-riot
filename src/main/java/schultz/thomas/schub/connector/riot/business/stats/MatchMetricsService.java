package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MatchPlayerMetrics;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Mêmes expressions que la grille GAME : une valeur de partie se lit directement sur la répartition du poste.
@Service
@RequiredArgsConstructor
public class MatchMetricsService {

    static final List<ReferenceMetric> PAR_PARTIE = ReferenceMetric.CATALOGUE.stream()
            .filter(ReferenceMetric::perGame)
            .toList();

    private final MongoTemplate mongo;

    public List<MatchPlayerMetrics> of(String matchId) {
        List<MatchPlayerMetrics> joueurs = new ArrayList<>();
        mongo.getCollection(MatchParticipation.COLLECTION)
                .aggregate(ReferencePipeline.valeursDePartie(matchId, PAR_PARTIE))
                .forEach(ligne -> joueurs.add(joueur(ligne)));
        return joueurs;
    }

    private static MatchPlayerMetrics joueur(Document ligne) {
        Map<String, Double> valeurs = new LinkedHashMap<>();
        PAR_PARTIE.forEach(metrique -> valeurs.put(metrique.key(),
                ligne.get(metrique.key()) instanceof Number n ? n.doubleValue() : null));
        Document rang = ligne.get("rank", Document.class);
        String position = ligne.getString("position");
        return new MatchPlayerMetrics(ligne.getString("puuid"),
                ligne.get("side") instanceof Number n ? n.intValue() : 0,
                position == null ? TeamPosition.UNKNOWN : TeamPosition.valueOf(position),
                ligne.get("championId") instanceof Number n ? n.intValue() : 0,
                rang == null ? null : rang.getString("tier"),
                rang != null && Boolean.TRUE.equals(rang.getBoolean("estimated")),
                valeurs);
    }
}
