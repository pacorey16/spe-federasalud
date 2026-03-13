package eu.europa.ec.simpl.edcconnectoradapter.model.edc.management.request;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class EdcAssetDefinition extends EdcAbstractManagement {

    private JsonNode dataAddress;

    public EdcAssetDefinition() {
        setProperties(new HashMap<>());
    }
}
