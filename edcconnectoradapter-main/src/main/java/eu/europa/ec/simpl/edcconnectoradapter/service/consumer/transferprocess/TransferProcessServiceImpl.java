package eu.europa.ec.simpl.edcconnectoradapter.service.consumer.transferprocess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.transfer.TransferProcess;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.transfer.TransferProcessId;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.transfer.TransferRequest;
import eu.europa.ec.simpl.data1.common.util.RemoteServiceUtil;
import eu.europa.ec.simpl.edcconnectoradapter.client.edcconnector.EDCConnectorTransferClient;
import eu.europa.ec.simpl.edcconnectoradapter.constant.Constants;
import eu.europa.ec.simpl.edcconnectoradapter.enumeration.EDCErrorType;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.common.response.EdcAcknowledgementId;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.transfer.request.EdcTransferRequest;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.transfer.response.EdcTransferProcess;
import eu.europa.ec.simpl.edcconnectoradapter.util.ModelUtil;
import eu.europa.ec.simpl.edcconnectoradapter.util.PathUtil;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Profile({"build", "test"})
@Log4j2
@Service
@RequiredArgsConstructor
public class TransferProcessServiceImpl implements TransferProcessService {

    protected static final String COMPLETED_STATUS = "COMPLETED";

    private static final String DEPROVISIONING_ENABLED_PROPERTY_NAME = "deprovisioningEnabled";

    @Value("${edc-connector.base-url}")
    private String edcConnectorBaseUrl;

    @Value("${edc-connector.api-key.value}")
    private String edcConnectorApiKeyValue;

    protected final EDCConnectorTransferClient edcConnectorTransferClient;

    protected URI edcConsumerManagementUrl;

    @PostConstruct
    public void init() {
        edcConsumerManagementUrl = URI.create(PathUtil.checkSuffix(Constants.EDC_MANAGEMENT_PATH, edcConnectorBaseUrl));
        log.info("init(): edcConsumerManagementUrl='{}'", edcConsumerManagementUrl);
    }

    @Override
    public TransferProcessId startTransfer(TransferRequest transferRequest) {
        String transferType = getTransferType(transferRequest);

        String providerProtocolUrl =
                PathUtil.checkSuffix(Constants.EDC_PROTOCOL_PATH, transferRequest.getProviderEndpoint());
        EdcTransferRequest edcTransferRequest = EdcTransferRequest.builder()
                .counterPartyAddress(providerProtocolUrl)
                .contractId(transferRequest.getContractId())
                .transferType(transferType)
                .dataDestination(normalizeDataDestination(transferRequest.getDataDestination()))
                .build();
        try {
            log.debug(
                    "startTransfer(): invoking edcConnectorClientApi.startTransferProcess() for {}",
                    edcTransferRequest);
            EdcAcknowledgementId acknowledgementId = edcConnectorTransferClient.startTransferProcess(
                    edcConsumerManagementUrl, createHttpHeadersMap(), edcTransferRequest);

            handleDeprovisioning(transferRequest, acknowledgementId);

            return ModelUtil.toTransferProcessId(acknowledgementId);
        } catch (FeignException e) {
            log.error("startTransfer() failed", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, "startTransferProcess operation failed", e);
        }
    }

    /** removes the deprovisioningEnabled property from the dataDestination as this is not expected by EDC Connector
     * @param dataDestination
     * @return a copy of the dataDestination without the deprovisioningEnabled property if it was present, otherwise the original dataDestination
     */
    private JsonNode normalizeDataDestination(JsonNode dataDestination) {
        if (!dataDestination.has(DEPROVISIONING_ENABLED_PROPERTY_NAME)) {
            return dataDestination;
        }
        ObjectNode copy = dataDestination.deepCopy();
        copy.remove(DEPROVISIONING_ENABLED_PROPERTY_NAME);
        return copy;
    }

    protected void handleDeprovisioning(TransferRequest transferRequest, EdcAcknowledgementId acknowledgementId) {
        log.info(
                "handleDeprovisioning() ignored by '{}' implementation",
                this.getClass().getName());
    }

    protected boolean isDeprovisioningEnabled(TransferRequest transferRequest) {
        JsonNode dataDestination = transferRequest.getDataDestination();
        return dataDestination.has(DEPROVISIONING_ENABLED_PROPERTY_NAME)
                && dataDestination.get(DEPROVISIONING_ENABLED_PROPERTY_NAME).asBoolean();
    }

    @Override
    public TransferProcess getTransferProcess(String transferProcessId) {
        EdcTransferProcess edcTransferProcess;
        try {
            log.debug(
                    "getTransferProcess(): invoking edcConnectorClientApi.getTransferProcess() for transferProcessId {}",
                    transferProcessId);
            edcTransferProcess = edcConnectorTransferClient.getTransferProcess(
                    edcConsumerManagementUrl, createHttpHeadersMap(), transferProcessId);
        } catch (FeignException e) {
            log.error("getTransferProcess() failed", e);
            throw RemoteServiceUtil.toRemoteServiceErrorException(
                    EDCErrorType.REMOTE_EDC_CONNECTOR_ERROR, "getTransferProcess operation failed", e);
        }

        String state = edcTransferProcess.getState();

        // this is missing in the kafka impl
        edcTransferProcess.setFinalState(state);

        return ModelUtil.transform(edcTransferProcess);
    }

    private String getTransferType(TransferRequest transferRequest) {
        // in the dataDestination template validated by validation service this field is mandatory
        return transferRequest.getDataDestination().get("type").textValue() + "-PUSH";
    }

    protected Map<String, String> createHttpHeadersMap() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.EDC_API_KEY_HEADER, edcConnectorApiKeyValue);
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
