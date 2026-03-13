package eu.europa.ec.simpl.edelivery.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InternalRequest {

    private String eDeliveryTriggerApi;
    private String appDataIdentifier;
    private String accessServiceIdentifier;
    private String requestorIdentifier;
    private String dateFrom;
    private String dateTo;
    private String format;
    private String service;
    private String action;

}
