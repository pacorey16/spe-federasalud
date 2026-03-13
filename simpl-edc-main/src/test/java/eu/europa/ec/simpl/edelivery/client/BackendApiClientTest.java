package eu.europa.ec.simpl.edelivery.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.edelivery.dto.EDeliveryRequest;
import eu.europa.ec.simpl.edelivery.dto.EDeliveryResponse;
import eu.europa.ec.simpl.edelivery.dto.InternalRequest;
import eu.europa.ec.simpl.edelivery.interceptor.RetryInterceptor;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackendApiClientTest {

    private InternalRequest internalRequest;

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Monitor monitor;

    private BackendApiClient backendApiClient;

    private RetryInterceptor retryInterceptor;

    @BeforeEach
    void setUp() {
        backendApiClient = new BackendApiClient(monitor, httpClient, new ObjectMapper());

        internalRequest = new InternalRequest(
                "https://valid-edelivery-api.com/trigger",
                "appDataId123",
                "accessServiceId456",
                "requestorId789",
                "2024-01-01",
                "2024-01-31",
                "JSON",
                "SomeService",
                "SomeAction");

        retryInterceptor = new RetryInterceptor(30000, 3000);

    }

    @Test
    void testSendTrigger_SuccessResponse() throws IOException {

        when(httpClient.interceptors()).thenReturn(List.of(retryInterceptor));

        when(httpClient.newCall(any())).thenReturn(Mockito.mock(Call.class));
        when(httpClient.newCall(any()).execute()).thenReturn(buildCorrectResponse());

        EDeliveryResponse response = backendApiClient.sendTriggerRequest(internalRequest, "valid-token");

        assertNotNull(response);
        assertEquals("success", response.getStatus());
        assertEquals("Request received", response.getMessage());
        assertNotNull(response.getOperationId());

    }

    @Test
    void testSendTrigger_IOExceptionResponse() throws IOException {

        when(httpClient.interceptors()).thenReturn(List.of(retryInterceptor));

        when(httpClient.newCall(any())).thenReturn(Mockito.mock(Call.class));
        when(httpClient.newCall(any()).execute()).thenThrow(new IOException("ErrorCode: 500, ErrorMessage: Internal Server Error"));

        assertThrows(EdcException.class, () -> backendApiClient.sendTriggerRequest(internalRequest, "valid-token"));

    }

    @Test
    void testSendTrigger_ConnectExceptionResponse() throws IOException {

        when(httpClient.interceptors()).thenReturn(List.of(retryInterceptor));

        when(httpClient.newCall(any())).thenReturn(Mockito.mock(Call.class));
        when(httpClient.newCall(any()).execute()).thenThrow(new ConnectException("Failed to connect to server"));

        assertThrows(EdcException.class, () -> backendApiClient.sendTriggerRequest(internalRequest, "valid-token"));

    }

    @Test
    void testSendTriggerRequestWithNullRequest() {
        assertThrows(NullPointerException.class, () -> {
            backendApiClient.sendTriggerRequest(null, "token");
        });
    }

    private Response buildCorrectResponse() throws JsonProcessingException {
        var objectWriter = new ObjectMapper().writer();

        var response = Map.of("operationId", "bfe71665-5c4a-4bd7-9961-4f3eccfcf017",
                "status", "success",
                "message", "Request received");
        var responseString = objectWriter.writeValueAsString(response);
        return new Response.Builder()
                .code(200)
                .message("")
                .body(ResponseBody.create(responseString, MediaType.get("application/json")))
                .protocol(Protocol.HTTP_1_1)
                .request(buildRequest())
                .build();
    }

    private Response buildErrorResponse() throws JsonProcessingException {
        var objectWriter = new ObjectMapper().writer();

        var response = Map.of("operationId", "cea32f41-4c55-4bbb-a935-6f921ef29253",
                "status", "error",
                "message", "Internal Server Error");
        var responseString = objectWriter.writeValueAsString(response);
        return new Response.Builder()
                .code(500)
                .message("")
                .body(ResponseBody.create(responseString, MediaType.get("application/json")))
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://any").build())
                .build();
    }

    private Request buildRequest() throws JsonProcessingException {
        var objectWriter = new ObjectMapper().writer();

        var json = objectWriter.writeValueAsString(EDeliveryRequest.builder()
                .accessServiceIdentifier("accessServiceId456")
                .requestorIdentifier("requestorId789")
                .appDataIdentifier("appDataId123")
                .parameters(Map.of(
                        "dateFrom", "2024-01-01",
                        "dateTo", "2024-01-31",
                        "format", "JSON",
                        "service", "SomeService",
                        "action", "SomeAction"))
                .build());

        RequestBody requestBody = RequestBody.create(json, MediaType.get(APPLICATION_JSON));

        return new Request.Builder().url("http://any")
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(AUTHORIZATION, "Bearer token")
                .post(requestBody)
                .build();
    }
}