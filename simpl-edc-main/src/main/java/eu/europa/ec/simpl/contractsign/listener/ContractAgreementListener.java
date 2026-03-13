package eu.europa.ec.simpl.contractsign.listener;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationListener;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;

import static java.lang.String.format;


/**
 * Listener for ContractAgreement Events to invoke external Contract manager to sign contract.
 *
 */
public class ContractAgreementListener implements ContractNegotiationListener {

    private EdcHttpClient httpClient;

    private String contractManagerUrl;

    private String contractManagerApiKey;

    private final Monitor monitor;

    private static final String DEFINITIONS_ENDPOINT = "definitions";
    private static final String CREDENTIALS_AGREEMENTS_ENDPOINT = "credentials/agreements";
    private static final String AGREEMENTS_ENDPOINT = "agreements";
    private static final String CONFIRMATION_ENDPOINT = "/status";

    private static final String ATTRIBUTE_CONTRACT_NEGOTIATION_ID = "contractNegotiationId";
    private static final String ATTRIBUTE_ASSET_ID = "assetId";
    private static final String ATTRIBUTE_PROVIDER_ID = "providerId";
    private static final String ATTRIBUTE_CONSUMER_ID = "consumerId";

    private static final String ATTRIBUTE_CONTRACT_OFFER_ID = "contractOfferId";

    /**
     * Convenience constructor for ContractAgreementListener.
     * @param monitor the monitor to use.
     * @param url the url of the contract manager to invoke.
     * @param apikey the apikey for the contract manager url.
     * @param httpClient the client to use for the webservice request.
     */
    public ContractAgreementListener(Monitor monitor, String url, String apikey, EdcHttpClient httpClient) {
        this.httpClient = httpClient;
        this.monitor = monitor;
        this.contractManagerUrl = url;
        this.contractManagerApiKey = apikey;
    }


    /**
     * Listen to contract agreement and invokes external Contract Manager.
     * The pendingguard should set negotiation to pending until call back methode is called
     * whether to continue or reject the requested contract negotiation.
     * 
     * @param negotiation the negotiation of interest.
     */
    @Override
    public void agreed(ContractNegotiation negotiation) {
        //invoke contractSignContractAgreement of contract manager on consumer side
        if (negotiation.getType() == ContractNegotiation.Type.CONSUMER) {
            String contractMgrUrl = getUrl(CREDENTIALS_AGREEMENTS_ENDPOINT, negotiation.getId(), negotiation.getContractOffers().get(0).getId());
            monitor.info("agreed for negotiation id " + negotiation.getId());
            invokeContractManager(negotiation, contractMgrUrl);
        }
    }

    /**
     * Listen to contract agreement and invokes external Contract Manager.
     * The pendingguard should set negotiation to pending until call back methode is called
     * whether to continue or reject the requested contract negotiation.
     * 
     * @param negotiation the negotiation of interest.
     */
    @Override
    public void verified(ContractNegotiation negotiation) {
        //invoke contractSignContractAgreement of contract manager on provider side
        if (negotiation.getType() == ContractNegotiation.Type.PROVIDER) {
            String contractMgrUrl = getUrl(CREDENTIALS_AGREEMENTS_ENDPOINT, negotiation.getContractAgreement().getId(), negotiation.getLastContractOffer().getId());
            monitor.info("verified for negotiation id " + negotiation.getId());
            invokeContractManager(negotiation, contractMgrUrl);
        }
    }

    @Override
    public void finalized(ContractNegotiation negotiation) {
        //invoke contractContractAgreementSignatureConfirmation of contract manager on provider side the negotiation has been finalized
        if (negotiation.getType() == ContractNegotiation.Type.PROVIDER) {
            String contractMgrUrl = getUrl(AGREEMENTS_ENDPOINT, negotiation.getContractAgreement().getId(), negotiation.getLastContractOffer().getId()) + CONFIRMATION_ENDPOINT;
            monitor.info("finalized for negotiation id " + negotiation.getId());
            invokeContractManager(negotiation, contractMgrUrl);
        }
    }

    @Override
    public void terminated(ContractNegotiation negotiation) {
        //invoke contractContractAgreementSignatureConfirmation of contract manager on provider side the negotiation has been finalized
        if (negotiation.getType() == ContractNegotiation.Type.PROVIDER) {
            String contractMgrUrl = getUrl(AGREEMENTS_ENDPOINT, negotiation.getContractAgreement().getId(), negotiation.getLastContractOffer().getId()) + CONFIRMATION_ENDPOINT;
            monitor.info("terminated for negotiation id " + negotiation.getId());
            invokeContractManager(negotiation, contractMgrUrl);
        }
    }

    private void invokeContractManager(ContractNegotiation negotiation, String endpoint) {
        var requestBuilder = new Request.Builder();

        RequestBody formBody;
        JsonObject contractDetails;
        MediaType json = MediaType.get("application/json");
        if (endpoint.endsWith(CONFIRMATION_ENDPOINT)) {
            //ContractConfirmation Body
            contractDetails = Json.createObjectBuilder().
                    add("status", ContractNegotiationStates.from(negotiation.getState()).name()).
                    build();
            formBody = RequestBody.create(contractDetails.toString(), json);
            requestBuilder = requestBuilder.patch(formBody);
        } else {
            //CreateContractAgreement Body
            contractDetails = Json.createObjectBuilder().
                    add(ATTRIBUTE_CONTRACT_NEGOTIATION_ID, negotiation.getId()).
                    add(ATTRIBUTE_ASSET_ID, negotiation.getContractAgreement().getAssetId()).
                    add(ATTRIBUTE_PROVIDER_ID, negotiation.getContractAgreement().getProviderId()).
                    add(ATTRIBUTE_CONSUMER_ID, negotiation.getContractAgreement().getConsumerId()).
                    add(ATTRIBUTE_CONTRACT_OFFER_ID, negotiation.getLastContractOffer().getId()).
                    build();
            formBody = RequestBody.create(contractDetails.toString(), json);
            requestBuilder = requestBuilder.post(formBody);
        }

        var request = requestBuilder
                .url(endpoint)
                .addHeader("x-api-key", contractManagerApiKey)
                .addHeader("accept", "application/json")
                .build();
        try (var response = httpClient.execute(request)) {
            if (!response.isSuccessful()) {
                monitor.severe(format("Error sending cloud event to endpoint %s, response status: %d", endpoint, response.code()));
            }
        } catch (IOException e) {
            monitor.severe(format("Error sending event to endpoint %s", endpoint), e);
        }
    }

    private String getUrl(String contractManagerEndpoint, String agreementId, String definitionId) {
        return "%s/%s/%s/%s/%s".formatted(contractManagerUrl, contractManagerEndpoint, agreementId, DEFINITIONS_ENDPOINT, definitionId);
    }
}
