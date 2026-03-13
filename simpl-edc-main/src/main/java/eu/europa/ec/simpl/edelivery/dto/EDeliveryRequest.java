package eu.europa.ec.simpl.edelivery.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EDeliveryRequest {

    @JsonProperty("accessServiceIdentifier")
    private String accessServiceIdentifier;

    @JsonProperty("requestorIdentifier")
    private String requestorIdentifier;

    @JsonProperty("appDataIdentifier")
    private String appDataIdentifier;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;

}
