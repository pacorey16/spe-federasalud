package eu.europa.ec.simpl.edelivery.datasink;

import eu.europa.ec.simpl.edelivery.client.BackendApiClient;
import eu.europa.ec.simpl.edelivery.datasource.EDeliveryDataSource;
import eu.europa.ec.simpl.edelivery.dto.EDeliveryResponse;
import eu.europa.ec.simpl.edelivery.dto.InternalRequest;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EDeliveryDataSinkTest {

    @Mock
    private BackendApiClient backendApiClient;

    @Mock
    private TypeManager typeManager;

    @Mock
    private EDeliveryDataSource dataSourcePart;

    @Mock
    private ExecutorService executorService;

    @Mock
    private Monitor monitor;

    private EDeliveryResponse successResponse;

    private EDeliveryResponse errorResponse;

    private EDeliveryDataSink eDeliveryDataSink;

    @BeforeEach
    void setUp() {
        eDeliveryDataSink = EDeliveryDataSink.Builder.newInstance()
                .requestId("request-id-123")
                .executorService(executorService)
                .monitor(monitor)
                .backendAPIClient(backendApiClient)
                .typeManager(typeManager)
                .accessServiceIdentifier("test-service-id")
                .requestorIdentifier("test-requestor-id")
                .dateFrom("2026-01-01")
                .dateTo("2026-12-31")
                .format("json")
                .service("test-service")
                .action("trigger")
                .build();

        successResponse = new EDeliveryResponse("ad20ca2f","success", "Trigger request accepted");
        errorResponse = new EDeliveryResponse("b4957f8d2b8f", "error", "Internal server error");
    }

    @Test
    void testTransferPartsSuccess() {
        when(dataSourcePart.getEDeliveryTriggerApi()).thenReturn("http://api.example.com");
        when(dataSourcePart.getAppDataIdentifier()).thenReturn("app-data-123");
        when(backendApiClient.sendTriggerRequest(any(InternalRequest.class), anyString()))
                .thenReturn(successResponse);

        List<DataSource.Part> parts = List.of(dataSourcePart);
        StreamResult<Object> result = eDeliveryDataSink.transferParts(parts);

        assertTrue(result.succeeded());
        verify(backendApiClient, times(1)).sendTriggerRequest(any(InternalRequest.class), eq("token"));
    }

    @Test
    void testTransferPartsMultiplePartsSuccess() {
        EDeliveryDataSource dataSource1 = mock(EDeliveryDataSource.class);
        EDeliveryDataSource dataSource2 = mock(EDeliveryDataSource.class);

        when(dataSource1.getEDeliveryTriggerApi()).thenReturn("http://api1.example.com");
        when(dataSource1.getAppDataIdentifier()).thenReturn("app-data-1");
        when(dataSource2.getEDeliveryTriggerApi()).thenReturn("http://api2.example.com");
        when(dataSource2.getAppDataIdentifier()).thenReturn("app-data-2");

        when(backendApiClient.sendTriggerRequest(any(InternalRequest.class), anyString()))
                .thenReturn(successResponse)
                .thenReturn(successResponse);

        List<DataSource.Part> parts = List.of(dataSource1, dataSource2);
        StreamResult<Object> result = eDeliveryDataSink.transferParts(parts);

        assertTrue(result.succeeded());
        verify(backendApiClient, times(2)).sendTriggerRequest(any(InternalRequest.class), eq("token"));
    }

    @Test
    void testTransferPartsError() {
        when(dataSourcePart.getEDeliveryTriggerApi()).thenReturn("http://api.example.com");
        when(dataSourcePart.getAppDataIdentifier()).thenReturn("app-data-123");
        when(backendApiClient.sendTriggerRequest(any(InternalRequest.class), anyString()))
                .thenReturn(errorResponse);

        List<DataSource.Part> parts = List.of(dataSourcePart);
        StreamResult<Object> result = eDeliveryDataSink.transferParts(parts);

        assertTrue(result.failed());
        assertTrue(result.getFailure().getMessages().stream()
                .anyMatch(msg -> msg.contains("Error sending trigger request")));
        verify(backendApiClient, times(1)).sendTriggerRequest(any(InternalRequest.class), eq("token"));
    }

    @Test
    void testTransferPartsMultiplePartsError() {
        EDeliveryDataSource dataSource1 = mock(EDeliveryDataSource.class);
        EDeliveryDataSource dataSource2 = mock(EDeliveryDataSource.class);

        when(dataSource1.getEDeliveryTriggerApi()).thenReturn("http://api1.example.com");
        when(dataSource1.getAppDataIdentifier()).thenReturn("app-data-1");
        when(dataSource2.getEDeliveryTriggerApi()).thenReturn("http://api2.example.com");
        when(dataSource2.getAppDataIdentifier()).thenReturn("app-data-2");

        when(backendApiClient.sendTriggerRequest(any(InternalRequest.class), anyString()))
                .thenReturn(successResponse)
                .thenReturn(errorResponse);

        List<DataSource.Part> parts = List.of(dataSource1, dataSource2);
        StreamResult<Object> result = eDeliveryDataSink.transferParts(parts);

        assertTrue(result.failed());
        verify(backendApiClient, times(2)).sendTriggerRequest(any(InternalRequest.class), eq("token"));
    }

    @Test
    void testInternalRequestBuiltCorrectly() {
        ArgumentCaptor<InternalRequest> requestCaptor = ArgumentCaptor.forClass(InternalRequest.class);

        when(dataSourcePart.getEDeliveryTriggerApi()).thenReturn("http://api.example.com");
        when(dataSourcePart.getAppDataIdentifier()).thenReturn("app-data-123");
        when(backendApiClient.sendTriggerRequest(any(InternalRequest.class), anyString()))
                .thenReturn(successResponse);

        eDeliveryDataSink.transferParts(List.of(dataSourcePart));

        verify(backendApiClient).sendTriggerRequest(requestCaptor.capture(), eq("token"));
        InternalRequest capturedRequest = requestCaptor.getValue();

        assertEquals("http://api.example.com", capturedRequest.getEDeliveryTriggerApi());
        assertEquals("app-data-123", capturedRequest.getAppDataIdentifier());
        assertEquals("test-service-id", capturedRequest.getAccessServiceIdentifier());
        assertEquals("test-requestor-id", capturedRequest.getRequestorIdentifier());
        assertEquals("2026-01-01", capturedRequest.getDateFrom());
        assertEquals("2026-12-31", capturedRequest.getDateTo());
        assertEquals("json", capturedRequest.getFormat());
        assertEquals("test-service", capturedRequest.getService());
        assertEquals("trigger", capturedRequest.getAction());

    }

    @Test
    void testBuilderValidation() {
        assertThrows(NullPointerException.class, () ->
                EDeliveryDataSink.Builder.newInstance()
                        .typeManager(typeManager)
                        .accessServiceIdentifier("test-service-id")
                        .requestorIdentifier("test-requestor-id")
                        .build()
        );
    }

    @Test
    void testEmptyPartsList() {
        StreamResult<Object> result = eDeliveryDataSink.transferParts(List.of());

        assertTrue(result.succeeded());
        verify(backendApiClient, never()).sendTriggerRequest(any(), any());
    }

}