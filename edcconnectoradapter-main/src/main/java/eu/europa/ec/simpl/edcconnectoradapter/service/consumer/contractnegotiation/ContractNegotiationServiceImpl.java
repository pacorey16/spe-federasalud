package eu.europa.ec.simpl.edcconnectoradapter.service.consumer.contractnegotiation;

import com.fasterxml.jackson.databind.JsonNode;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.catalog.CatalogSearchResult;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.contract.ContractNegotiation;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.contract.ContractNegotiationId;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.contract.ContractNegotiationRequest;
import eu.europa.ec.simpl.data1.common.util.RemoteServiceUtil;
import eu.europa.ec.simpl.edcconnectoradapter.client.edcconnector.EDCConnectorCatalogClient;
import eu.europa.ec.simpl.edcconnectoradapter.client.edcconnector.EDCConnectorContractClient;
import eu.europa.ec.simpl.edcconnectoradapter.constant.Constants;
import eu.europa.ec.simpl.edcconnectoradapter.enumeration.EDCErrorType;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.catalog.request.EdcCatalogRequest;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.catalog.request.EdcCriterion;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.catalog.request.EdcQueryPayload;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.common.response.EdcAcknowledgementId;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.contract.request.EdcContractNegotiationRequest;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.contract.response.EdcContractNegotiation;
import eu.europa.ec.simpl.edcconnectoradapter.service.catalogmapper.CatalogMapperService;
import eu.europa.ec.simpl.edcconnectoradapter.util.ModelUtil;
import eu.europa.ec.simpl.edcconnectoradapter.util.PathUtil;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Profile({"consumer", "build", "test"})
@Log4j2
@Service
@RequiredArgsConstructor
public class ContractNegotiationServiceImpl implements ContractNegotiationService {

    @Value("${edc-connector.base-url}")
    private String edcConnectorBaseUrl;

    @Value("${edc-connector.api-key.value}")
    private String edcConnectorApiKeyValue;

    private final EDCConnectorCatalogClient edcConnectorCatalogClient;
    private final EDCConnectorContractClient edcConnectorContractClient;
    private final CatalogMapperService catalogMapperService;

    protected URI edcConsumerManagementUrl;

    @PostConstruct
    public void init() {
        edcConsumerManagementUrl = URI.create(PathUtil.checkSuffix(Constants.EDC_MANAGEMENT_PATH, edcConnectorBaseUrl));
        log.info("init(): edcConsumerManagementUrl='{}'", edcConsumerManagementUrl);
    }

    @Override
    public CatalogSearchResult getCatalog(ContractNegotiationRequest request) {
        JsonNode edcResponse = requestCatalog(request);
        return catalogMapperService.mapEdcCatalog(edcResponse, request.getContractDefinitionId());
    }

    private JsonNode requestCatalog(ContractNegotiationRequest request) {
        log.debug("requestCatalog() for {}", request);

        String assetId = request.getAssetId();

        List<EdcCriterion> criteria = new ArrayList<>();
        criteria.add(EdcCriterion.builder()
                .operandLeft(Constants.EDC_PREFIX + Constants.EDC_ASSET_ID_PROPERTY)
                .operator(Constants.EDC_EQUAL)
                .operandRight(assetId)
                .build());

        EdcQueryPayload edcQueryPayload = EdcQueryPayload.builder()
                .offset(0)
                .limit(Constants.DEFAULT_EDC_QUERY_LIMIT)
                .filterExpression(criteria)
                .build();

        String providerProtocolUrl = PathUtil.checkSuffix(Constants.EDC_PROTOCOL_PATH, request.getProviderEndpoint());

        EdcCatalogRequest edcCatalogRequest = EdcCatalogRequest.builder()
                .counterPartyAddress(providerProtocolUrl)
                .querySpec(edcQueryPayload)
                .build();
        try {
            log.debug(
                    "requestCatalog(): invoking edcConnectorCatalogClient.requestCatalog() for {}", edcCatalogRequest);
            return edcConnectorCatalogClient.requestCatalog(edcConsumerManagementUrl, getHeaders(), edcCatalogRequest);
        } catch (FeignException e) {
            log.error("requestCatalog() failed", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, "requestCatalog operation failed", e);
        }
    }

    @Override
    public ContractNegotiationId initiateContractNegotiation(ContractNegotiationRequest request) {
        log.debug("initiateContractNegotiation() for {}", request);
        JsonNode providerCatalog = requestCatalog(request);
        JsonNode offerForNegotiation = catalogMapperService.mapEdcCatalogOfferForNegotiation(
                providerCatalog, request.getContractDefinitionId());

        String providerProtocolUrl = PathUtil.checkSuffix(Constants.EDC_PROTOCOL_PATH, request.getProviderEndpoint());

        EdcContractNegotiationRequest edcContractNegotiationRequest = EdcContractNegotiationRequest.builder()
                .counterPartyAddress(providerProtocolUrl)
                .policy(offerForNegotiation)
                .build();

        try {
            log.debug(
                    "initiateContractNegotiation(): invoking edcConnectorContractClient.initiateContractNegotiation() for {}",
                    edcContractNegotiationRequest);
            EdcAcknowledgementId acknowledgementId = edcConnectorContractClient.initiateContractNegotiation(
                    edcConsumerManagementUrl, getHeaders(), edcContractNegotiationRequest);
            return ModelUtil.toContractNegotiationId(acknowledgementId);
        } catch (FeignException e) {
            log.error("initiateContractNegotiation() failed", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, "initiateContractNegotiation operation failed", e);
        }
    }

    @Override
    public ContractNegotiation getContractNegotiation(String negotiationId) {
        try {
            log.debug(
                    "getContractNegotiation(): invoking edcConnectorContractClient.getContractNegotiation() for negotiationId {}",
                    negotiationId);
            EdcContractNegotiation edcContractNegotiation = edcConnectorContractClient.getContractNegotiation(
                    edcConsumerManagementUrl, getHeaders(), negotiationId);
            return ModelUtil.transform(edcContractNegotiation);
        } catch (FeignException e) {
            log.error("getContractNegotiation() failed", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, "getContractNegotiation operation failed", e);
        }
    }

    protected Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.EDC_API_KEY_HEADER, edcConnectorApiKeyValue);
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
