package eu.europa.ec.simpl.contractsign.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.FINALIZED;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates.TERMINATED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractAgreementListenerTest {

    private final ContractNegotiation contractNegotiationConsumer = createContractNegotiationBuilder("cn1", ContractNegotiation.Type.CONSUMER, FINALIZED.code())
            .contractAgreement(createContractAgreement("cn1")).build();

    private final ContractNegotiation contractNegotiationProvider = createContractNegotiationBuilder("cn2", ContractNegotiation.Type.PROVIDER, FINALIZED.code())
            .contractAgreement(createContractAgreement("cn2")).build();

    private final ContractNegotiation contractNegotiationProviderTerminated = createContractNegotiationBuilder("cn3", ContractNegotiation.Type.PROVIDER, TERMINATED.code())
            .contractAgreement(createContractAgreement("cn3")).build();

    private final EdcHttpClient httpClient = mock();

    private final String endpointContractManager = "http://host" ;

    private final String contractManagerApiKey = "gfhfj74949";

    private final Monitor monitor = mock();

    private final ContractAgreementListener listener = new ContractAgreementListener(monitor, endpointContractManager, contractManagerApiKey, httpClient);

    @Test
    void agreedShouldProceedCorrectlyProviderNoHttpCall() throws IOException {
        ContractNegotiation negotiation = mock();
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);
        assertDoesNotThrow(() -> listener.agreed(negotiation));
        verify(httpClient, Mockito.times(0)).execute(any());
    }

    @Test
    void agreedShouldProceedCorrectlyConsumer() throws IOException {
        when(httpClient.execute(any())).thenReturn(buildCorrectResponse());
        assertDoesNotThrow(() -> listener.agreed(contractNegotiationConsumer));
        verify(monitor, Mockito.times(0)).severe(any());
    }

    @Test
    void agreedShouldProceedWrongConsumer400Response() throws IOException {
        var ow = new ObjectMapper().writer();
        var body = Map.of("result", "Not found");
        var bodyString = ow.writeValueAsString(body);
        Response response = new Response.Builder()
                .code(404)
                .message("Not found")
                .body(ResponseBody.create(bodyString, MediaType.get("application/json")))
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://any").build())
                .build();

        when(httpClient.execute(any())).thenReturn(response);

        assertDoesNotThrow(() -> listener.agreed(contractNegotiationConsumer));
        verify(monitor, Mockito.times(1)).severe("Error sending cloud event to endpoint http://host/credentials/agreements/cn1/definitions/test-offer-id, response status: 404");
    }

    @Test
    void agreedShouldProceedWrongConsumerIOEException() throws IOException {
        when(httpClient.execute(any())).thenThrow(IOException.class);
        assertDoesNotThrow(() -> listener.agreed(contractNegotiationConsumer));
        verify(monitor, Mockito.times(1)).severe(eq("Error sending event to endpoint http://host/credentials/agreements/cn1/definitions/test-offer-id"), isA(IOException.class));
    }

    @Test
    void verifiedShouldProceedCorrectlyConsumerNoHttpCall() throws IOException {
        ContractNegotiation negotiation = mock();
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.CONSUMER);
        assertDoesNotThrow(() -> listener.verified(negotiation));
        verify(httpClient, Mockito.times(0)).execute(any());
    }

    @Test
    void verifiedShouldProceedCorrectlyProvider() throws IOException {
        when(httpClient.execute(any())).thenReturn(buildCorrectResponse());
        assertDoesNotThrow(() -> listener.verified(contractNegotiationProvider));
        verify(monitor, Mockito.times(0)).severe(any());
    }

    @Test
    void finalizedShouldProceedCorrectlyConsumerNoHttpCall() throws IOException {
        ContractNegotiation negotiation = mock();
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.CONSUMER);
        assertDoesNotThrow(() -> listener.finalized(negotiation));
        verify(httpClient, Mockito.times(0)).execute(any());
    }

    @Test
    void finalizedShouldProceedCorrectlyProvider() throws IOException {
        when(httpClient.execute(any())).thenReturn(buildCorrectResponse());
        assertDoesNotThrow(() -> listener.terminated(contractNegotiationProvider));
        verify(monitor, Mockito.times(0)).severe(any());
    }

    @Test
    void terminatedShouldProceedCorrectlyConsumerNoHttpCall() throws IOException {
        ContractNegotiation negotiation = mock();
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.CONSUMER);
        assertDoesNotThrow(() -> listener.terminated(negotiation));
        verify(httpClient, Mockito.times(0)).execute(any());
    }

    @Test
    void terminatedShouldProceedCorrectlyProvider() throws IOException {
        when(httpClient.execute(any())).thenReturn(buildCorrectResponse());
        assertDoesNotThrow(() -> listener.terminated(contractNegotiationProviderTerminated));
        verify(monitor, Mockito.times(0)).severe(any());
    }


    private ContractNegotiation.Builder createContractNegotiationBuilder(String negotiationId, ContractNegotiation.Type type, int state) {
        return ContractNegotiation.Builder.newInstance()
                .id(negotiationId)
                .type(type)
                .counterPartyId(UUID.randomUUID().toString())
                .counterPartyAddress("address")
                .callbackAddresses(List.of(CallbackAddress.Builder.newInstance()
                        .uri("local://test")
                        .events(Set.of("test-event1", "test-event2"))
                        .build()))
                .protocol("dataspace-protocol-http")
                .contractOffer(contractOfferBuilder().build())
                .state(state);
    }

    private ContractOffer.Builder contractOfferBuilder() {
        return ContractOffer.Builder.newInstance()
                .id("test-offer-id")
                .assetId("test-asset-id")
                .policy(Policy.Builder.newInstance().build());
    }

    private ContractAgreement createContractAgreement(String negotiationId) {
        return ContractAgreement.Builder.newInstance()
                .id(negotiationId)
                .assetId(UUID.randomUUID().toString())
                .consumerId(UUID.randomUUID() + "-consumer")
                .providerId(UUID.randomUUID() + "-provider")
                .policy(Policy.Builder.newInstance().build())
                .build();
    }

    private Response buildCorrectResponse() throws JsonProcessingException {
        var ow = new ObjectMapper().writer();
        var body = Map.of("result", "OK");
        var bodyString = ow.writeValueAsString(body);
        return new Response.Builder()
                .code(200)
                .message("ok")
                .body(ResponseBody.create(bodyString, MediaType.get("application/json")))
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://any").build())
                .build();
    }
}
