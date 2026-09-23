package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PatchStart;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.Date;
import java.util.List;

// Début d'un patch = sa première partie collectée : ce qui compte, c'est la fenêtre qu'il ouvre dans nos données.
@Service
@RequiredArgsConstructor
public class PatchCalendar {

    static final String INDEX = "patch_startedAt";

    private final MongoTemplate mongo;

    public List<PatchStart> recent(int count) {
        mongo.indexOps(MatchParticipation.COLLECTION).ensureIndex(new Index()
                .on("patch", Sort.Direction.ASC).on("startedAt", Sort.Direction.ASC).named(INDEX));
        return mongo.findDistinct(new Query(), "patch", MatchParticipation.COLLECTION, String.class).stream()
                .filter(patch -> patch != null && patch.matches("\\d+\\.\\d+"))
                .sorted(ReferenceService.parVersion().reversed())
                .limit(count)
                .map(patch -> new PatchStart(patch, debut(patch)))
                .filter(start -> start.startedAt() != null)
                .toList();
    }

    private Instant debut(String patch) {
        Query premiere = Query.query(Criteria.where("patch").is(patch).and("startedAt").ne(null))
                .with(Sort.by(Sort.Direction.ASC, "startedAt")).limit(1);
        premiere.fields().include("startedAt").exclude("_id");
        Document ligne = mongo.findOne(premiere, Document.class, MatchParticipation.COLLECTION);
        if (ligne != null && ligne.get("startedAt") instanceof Date date) {
            return date.toInstant();
        }
        return null;
    }
}
