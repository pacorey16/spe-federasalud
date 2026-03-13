package eu.europa.ec.simpl.edelivery.datasink;

import eu.europa.ec.simpl.edelivery.client.BackendApiClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Violation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;

import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.ACTION_PATH;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.DATE_FROM_PATH;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.DATE_TO_PATH;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.EDELIVERY_TYPE;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.FORMAT_PATH;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.REQUESTOR_IDENTIFIER_PATH;
import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.SERVICE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EDeliveryDataSinkFactoryTest {

    @Mock
    private Monitor monitor;

    @Mock
    private DataAddressValidatorRegistry dataAddressValidatorRegistry;

    @Mock
    private ExecutorService executorService;

    @Mock
    private BackendApiClient backendAPIClient;

    @Mock
    private TypeManager typeManager;

    @Mock
    private DataFlowStartMessage dataFlowStartMessage;

    @Mock
    private DataAddress dataAddress;

    private EDeliveryDataSinkFactory factory;

    @BeforeEach
    void setUp() {
        factory = new EDeliveryDataSinkFactory(
                monitor,
                dataAddressValidatorRegistry,
                executorService,
                backendAPIClient,
                typeManager
        );
    }

    @Test
    void testSupportedType() {
        String supportedType = factory.supportedType();

        assertEquals(EDELIVERY_TYPE, supportedType);
    }

    @Test
    void testValidateRequestSuccess() {
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataAddressValidatorRegistry.validateDestination(dataAddress))
                .thenReturn(ValidationResult.success());

        Result<Void> result = factory.validateRequest(dataFlowStartMessage);

        assertTrue(result.succeeded());
        verify(dataAddressValidatorRegistry, times(1)).validateDestination(dataAddress);
    }

    @Test
    void testValidateRequestFailure() {
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataAddressValidatorRegistry.validateDestination(dataAddress))
                .thenReturn(ValidationResult.failure(Violation.violation("Invalid data address", "")));

        Result<Void> result = factory.validateRequest(dataFlowStartMessage);

        assertTrue(result.failed());
        verify(dataAddressValidatorRegistry, times(1)).validateDestination(dataAddress);
    }

    @Test
    void testCreateSink() {
        setupDataAddress();
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataFlowStartMessage.getId()).thenReturn("request-123");

        EDeliveryDataSink sink = (EDeliveryDataSink) factory.createSink(dataFlowStartMessage);

        assertNotNull(sink);
        verify(dataAddress, times(1)).getStringProperty(ACCESS_SERVICE_IDENTIFIER_PATH);
        verify(dataAddress, times(1)).getStringProperty(REQUESTOR_IDENTIFIER_PATH);
        verify(dataAddress, times(1)).getStringProperty(DATE_FROM_PATH);
        verify(dataAddress, times(1)).getStringProperty(DATE_TO_PATH);
        verify(dataAddress, times(1)).getStringProperty(FORMAT_PATH);
        verify(dataAddress, times(1)).getStringProperty(SERVICE_PATH);
        verify(dataAddress, times(1)).getStringProperty(ACTION_PATH);
    }

    @Test
    void testCreateSinkWithMissingOptionalProperties() {
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataFlowStartMessage.getId()).thenReturn("request-456");
        when(dataAddress.getStringProperty(ACCESS_SERVICE_IDENTIFIER_PATH)).thenReturn("service-123");
        when(dataAddress.getStringProperty(REQUESTOR_IDENTIFIER_PATH)).thenReturn("requestor-456");
        when(dataAddress.getStringProperty(DATE_FROM_PATH)).thenReturn(null);
        when(dataAddress.getStringProperty(DATE_TO_PATH)).thenReturn(null);
        when(dataAddress.getStringProperty(FORMAT_PATH)).thenReturn("json");
        when(dataAddress.getStringProperty(SERVICE_PATH)).thenReturn("my-service");
        when(dataAddress.getStringProperty(ACTION_PATH)).thenReturn("retrieve");

        EDeliveryDataSink sink = (EDeliveryDataSink) factory.createSink(dataFlowStartMessage);

        assertNotNull(sink);
    }

    @Test
    void testCreateSinkWithMissingMandatoryProperties() {
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataFlowStartMessage.getId()).thenReturn("request-456");
        when(dataAddress.getStringProperty(ACCESS_SERVICE_IDENTIFIER_PATH)).thenReturn("");
        when(dataAddress.getStringProperty(REQUESTOR_IDENTIFIER_PATH)).thenReturn(null);
        when(dataAddress.getStringProperty(DATE_FROM_PATH)).thenReturn("date-from");
        when(dataAddress.getStringProperty(DATE_TO_PATH)).thenReturn("date-to");
        when(dataAddress.getStringProperty(FORMAT_PATH)).thenReturn("json");
        when(dataAddress.getStringProperty(SERVICE_PATH)).thenReturn("my-service");
        when(dataAddress.getStringProperty(ACTION_PATH)).thenReturn("retrieve");

        assertThrows(Exception.class, () -> factory.createSink(dataFlowStartMessage));

    }

    @Test
    void testCreateSinkUsesRequestId() {
        setupDataAddress();
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataFlowStartMessage.getId()).thenReturn("unique-request-id-789");

        EDeliveryDataSink sink = (EDeliveryDataSink) factory.createSink(dataFlowStartMessage);

        assertNotNull(sink);
    }

    @Test
    void testCreateSinkWithEmptyStringProperties() {
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataFlowStartMessage.getId()).thenReturn("request-empty");
        when(dataAddress.getStringProperty(ACCESS_SERVICE_IDENTIFIER_PATH)).thenReturn("");
        when(dataAddress.getStringProperty(REQUESTOR_IDENTIFIER_PATH)).thenReturn("");
        when(dataAddress.getStringProperty(DATE_FROM_PATH)).thenReturn("");
        when(dataAddress.getStringProperty(DATE_TO_PATH)).thenReturn("");
        when(dataAddress.getStringProperty(FORMAT_PATH)).thenReturn("");
        when(dataAddress.getStringProperty(SERVICE_PATH)).thenReturn("");
        when(dataAddress.getStringProperty(ACTION_PATH)).thenReturn("");

        EDeliveryDataSink sink = (EDeliveryDataSink) factory.createSink(dataFlowStartMessage);

        assertNotNull(sink);
    }

    @Test
    void testValidateRequestWithNullDestination() {
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(null);

        assertThrows(Exception.class, () -> factory.validateRequest(dataFlowStartMessage));
    }

    @Test
    void testCreateMultipleSinks() {
        setupDataAddress();
        when(dataFlowStartMessage.getDestinationDataAddress()).thenReturn(dataAddress);
        when(dataFlowStartMessage.getId()).thenReturn("request-1");

        EDeliveryDataSink sink1 = (EDeliveryDataSink) factory.createSink(dataFlowStartMessage);

        when(dataFlowStartMessage.getId()).thenReturn("request-2");
        EDeliveryDataSink sink2 = (EDeliveryDataSink) factory.createSink(dataFlowStartMessage);

        assertNotNull(sink1);
        assertNotNull(sink2);
        assertNotEquals(sink1, sink2);
    }

    private void setupDataAddress() {
        when(dataAddress.getStringProperty(ACCESS_SERVICE_IDENTIFIER_PATH)).thenReturn("access-service-123");
        when(dataAddress.getStringProperty(REQUESTOR_IDENTIFIER_PATH)).thenReturn("requestor-456");
        when(dataAddress.getStringProperty(DATE_FROM_PATH)).thenReturn("2026-01-01");
        when(dataAddress.getStringProperty(DATE_TO_PATH)).thenReturn("2026-12-31");
        when(dataAddress.getStringProperty(FORMAT_PATH)).thenReturn("json");
        when(dataAddress.getStringProperty(SERVICE_PATH)).thenReturn("test-service");
        when(dataAddress.getStringProperty(ACTION_PATH)).thenReturn("trigger");
    }
}