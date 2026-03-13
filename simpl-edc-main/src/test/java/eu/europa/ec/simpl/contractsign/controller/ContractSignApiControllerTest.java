package eu.europa.ec.simpl.contractsign.controller;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ApiTest
class ContractSignApiControllerTest extends RestControllerTestBase {

    private final Monitor monitor = mock();
    private final ContractNegotiationService contractNegotiationService = mock();
    private final EventRouter eventRouter = mock();
    private final Clock clock = Clock.systemDefaultZone();
    private final ContractNegotiationStore negotiationStore = mock();

    @Override
    protected Object controller() {
        return new ContractSignApiController(monitor, contractNegotiationService, negotiationStore, eventRouter, clock);
    }

    @Deprecated
    @Test
    void callbackSignedShouldReturn400NoNegotiationFound() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        when(contractNegotiationService.findbyId(id)).thenReturn(null);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/true")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(400)
                .body(containsString("unknown negotiation id"));
    }

    @Deprecated
    @Test
    void callbackSignedShouldReturn400IncorrectState() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.TERMINATED.code());
        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/true")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(400)
                .body(containsString("negotiation has invalid negotiation state"));
    }

    @Deprecated
    @Test
    void callbackNotSignedShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFIED.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/false")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(negotiationStore, Mockito.times(1)).save(negotiation);
    }

    @Deprecated
    @Test
    void callbackSignedVerifiedProviderShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFIED.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);
        when(negotiation.getId()).thenReturn(id);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/true")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(negotiationStore, Mockito.times(1)).save(negotiation);
        verify(eventRouter, Mockito.times(1)).publish(any());
    }

    @Deprecated
    @Test
    void callbackSignedVerifiedConsumerShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFIED.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.CONSUMER);
        when(negotiation.getId()).thenReturn(id);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/true")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(negotiationStore, Mockito.times(0)).save(negotiation);
    }

    @Deprecated
    @Test
    void callbackSignedVerifyingShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFYING.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);
        when(negotiation.getId()).thenReturn(id);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/true")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(eventRouter, Mockito.times(1)).publish(any());
        verify(negotiationStore, Mockito.times(1)).save(negotiation);
    }

    @Test
    void testCallbackStatusSignedShouldReturn400NoNegotiationFound() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        when(contractNegotiationService.findbyId(id)).thenReturn(null);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/signed")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(400)
                .body(containsString("unknown negotiation id"));
    }

    @Test
    void testCallbackStatusSignedShouldReturn400IncorrectState() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.TERMINATED.code());
        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/signed")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(400)
                .body(containsString("negotiation has invalid negotiation state"));
    }

    @Test
    void testCallbackStatusNotSignedShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFIED.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/rejected")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(negotiationStore, Mockito.times(1)).save(negotiation);
    }

    @Test
    void testCallbackStatusSignedVerifiedProviderShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFIED.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);
        when(negotiation.getId()).thenReturn(id);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/signed")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(negotiationStore, Mockito.times(1)).save(negotiation);
        verify(eventRouter, Mockito.times(1)).publish(any());
    }

    @Test
    void testCallbackStatusSignedVerifiedConsumerShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFIED.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.CONSUMER);
        when(negotiation.getId()).thenReturn(id);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/signed")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(negotiationStore, Mockito.times(0)).save(negotiation);
    }

    @Test
    void testCallbackStatusSignedVerifyingShouldReturn200() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        ContractNegotiation negotiation = mock();
        when(negotiation.getState()).thenReturn(ContractNegotiationStates.VERIFYING.code());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);
        when(negotiation.getId()).thenReturn(id);

        when(contractNegotiationService.findbyId(id)).thenReturn(negotiation);

        given()
                .contentType("application/json")
                .baseUri("http://localhost:" + port + "/signed/" + id + "/signed")
                .contentType(JSON)
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200);

        verify(eventRouter, Mockito.times(1)).publish(any());
        verify(negotiationStore, Mockito.times(1)).save(negotiation);
    }
}
