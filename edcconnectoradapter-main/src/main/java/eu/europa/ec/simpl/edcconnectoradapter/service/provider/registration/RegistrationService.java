package eu.europa.ec.simpl.edcconnectoradapter.service.provider.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import eu.europa.ec.simpl.data1.common.enumeration.OfferType;
import org.json.JSONObject;

public interface RegistrationService {

    /**
     * @param sdJsonLd  Json-Ld
     * @param ecosystem Ecosystem
     * @return enriched result
     */
    JSONObject enrich(JSONObject sdJsonLd, String ecosystem);

    /**
     * @param sdJsonLd      Json-Ld
     * @param ecosystem     Ecosystem
     * @param offerType OfferType
     * @return registered result
     * @throws JsonProcessingException
     */
    JSONObject register(JSONObject sdJsonLd, String ecosystem, OfferType offerType) throws JsonProcessingException;
}
