package schultz.thomas.schub.connector.riot.business.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotMatchResponse;

// ObjectMapper propre : le stocké porte bien plus de champs que RiotMatchResponse, l'inconnu doit être
// ignoré quelle que soit la configuration Jackson de l'application.
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
        return toDetail(decode(raw));
    }

    public MatchDetail toDetail(RiotMatchResponse response) {
        return matchMapper.toDomain(response);
    }
}
