package schultz.thomas.schub.connector.riot.data.repository;

import schultz.thomas.schub.connector.riot.data.model.KnownAccount;

import java.util.List;

public interface KnownAccountRepositoryCustom {

    // N'écrit un compte que si l'observation est plus récente que celle en base : plusieurs ouvriers écrivent en même temps.
    int saveIfNewer(List<KnownAccount> comptes);
}
