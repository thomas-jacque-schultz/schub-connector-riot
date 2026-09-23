package schultz.thomas.schub.connector.riot.data.repository;

import com.mongodb.bulk.BulkWriteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import schultz.thomas.schub.connector.riot.data.model.KnownAccount;

import java.util.List;

@RequiredArgsConstructor
public class KnownAccountRepositoryCustomImpl implements KnownAccountRepositoryCustom {

    private static final int CLE_EN_DOUBLE = 11000;

    private final MongoTemplate mongo;

    // Le filtre écarte le compte déjà plus récent ; l'upsert tente alors une insertion que l'_id refuse : ce refus
    // veut dire « une observation plus récente est en place », pas une erreur.
    @Override
    public int saveIfNewer(List<KnownAccount> comptes) {
        if (comptes.isEmpty()) {
            return 0;
        }
        BulkOperations lot = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, KnownAccount.class);
        for (KnownAccount compte : comptes) {
            lot.upsert(Query.query(Criteria.where("_id").is(compte.puuid()).orOperator(
                            Criteria.where("observedAt").lt(compte.observedAt()),
                            Criteria.where("observedAt").is(null))),
                    new Update().set("gameName", compte.gameName())
                            .set("tagLine", compte.tagLine())
                            .set("searchName", compte.searchName())
                            .set("observedAt", compte.observedAt())
                            .set("source", compte.source()));
        }
        try {
            return ecrits(lot.execute());
        } catch (BulkOperationException refus) {
            if (refus.getErrors().stream().allMatch(erreur -> erreur.getCode() == CLE_EN_DOUBLE)) {
                return ecrits(refus.getResult());
            }
            throw refus;
        }
    }

    private static int ecrits(BulkWriteResult resultat) {
        return resultat.getUpserts().size() + resultat.getModifiedCount();
    }
}
