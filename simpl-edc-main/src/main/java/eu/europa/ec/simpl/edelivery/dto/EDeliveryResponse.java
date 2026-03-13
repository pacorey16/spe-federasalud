package eu.europa.ec.simpl.edelivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EDeliveryResponse {

    @JsonProperty("operationId")
    private String operationId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

}
