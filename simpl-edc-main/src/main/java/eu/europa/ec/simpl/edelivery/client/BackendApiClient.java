package eu.europa.ec.simpl.edelivery.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.edelivery.dto.EDeliveryResponse;
import eu.europa.ec.simpl.edelivery.dto.InternalRequest;
import eu.europa.ec.simpl.edelivery.dto.EDeliveryRequest;
import eu.europa.ec.simpl.edelivery.interceptor.RetryInterceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Map;

import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.API_MAX_RETRY_TIME;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.API_RETRY_DELAY;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;

public class BackendApiClient {

    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;
    private Monitor monitor;

    public BackendApiClient(Monitor monitor, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.monitor = monitor;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public EDeliveryResponse sendTriggerRequest(InternalRequest internalRequest, String authToken) {

        String endpoint = internalRequest.getEDeliveryTriggerApi();
        String appDataIdentifier = internalRequest.getAppDataIdentifier();
        String accessServiceIdentifier = internalRequest.getAccessServiceIdentifier();
        String requestorIdentifier = internalRequest.getRequestorIdentifier();
        String dateFrom = internalRequest.getDateFrom();
        String dateTo = internalRequest.getDateTo();
        String format = internalRequest.getFormat();
        String service = internalRequest.getService();
        String action = internalRequest.getAction();

        Map<String, Object> parameters = Map.of("dateFrom", dateFrom,
                "dateTo", dateTo,
                "format", format,
                "service", service,
                "action", action);

        EDeliveryRequest payload = new EDeliveryRequest(accessServiceIdentifier, requestorIdentifier, appDataIdentifier, parameters);

        try {
            return sendRequest(endpoint, authToken, appDataIdentifier, payload, EDeliveryResponse.class);
        } catch (Exception e) {
            String errorMessage = format("EDelivery unexpected error for appDataIdentifier %s : %s", appDataIdentifier, e.getMessage());
            monitor.severe(errorMessage);
            throw new EdcException(errorMessage);
        }
    }

    private <T> T sendRequest(String endpoint, String authToken, String appDataIdentifier, Object payload, Class<T> clazz) throws Exception {

        var request = buildRequest(endpoint, authToken, payload);

        if (httpClient.interceptors().stream()
                .filter(interceptor -> interceptor instanceof RetryInterceptor)
                .findFirst()
                .isEmpty()) {

            httpClient = httpClient.newBuilder()
                    .addInterceptor(new RetryInterceptor(API_MAX_RETRY_TIME, API_RETRY_DELAY))
                    .build();
        }

        try (var response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                String warnMessage = format("Response without body. appDataIdentifier: %s, statusCode: %s", appDataIdentifier, response.code());
                monitor.warning(warnMessage);
                throw new EdcException(warnMessage);
            }
            return objectMapper.readValue(response.body().string(), clazz);
        }
    }

    private Request buildRequest(String endpoint, String authToken, Object payload) {

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new EdcException("Error parsing payload to JSON", e);
        }

        Request.Builder requestBuilder = new Request.Builder();
        RequestBody requestBody = RequestBody.create(json, MediaType.get(APPLICATION_JSON));

        return requestBuilder
                .url(endpoint)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(AUTHORIZATION, "Bearer " + authToken)
                .post(requestBody)
                .build();
    }

}