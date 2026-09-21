package schultz.thomas.schub.connector.riot.business.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotMatchResponse;

/**
 * Relit une partie stockée : tout ce qui en dérive se recalcule ainsi, sans appel.
 *
 * <p>Mapper propre plutôt que le bean de Boot : le stocké porte bien plus de champs que
 * {@link RiotMatchResponse} n'en déclare, et l'inconnu doit être ignoré quoi qu'il arrive à la
 * configuration Jackson de l'application.</p>
 */
@RequiredArgsConstructor
@Component
public class RawMatchDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final MatchMapper matchMapper;

    public RiotMatchResponse decode(Document raw) {
        return MAPPER.convertValue(raw, RiotMatchResponse.class);
    }

    public MatchDetail toDetail(Document raw) {
        return matchMapper.toDomain(decode(raw));
    }
}
