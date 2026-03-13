package eu.europa.ec.simpl.edelivery.datasource;

import eu.europa.ec.simpl.edelivery.utils.EDeliverySchema;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Result;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EDeliveryDataSourceFactoryTest {

    @Mock
    private DataAddressValidatorRegistry dataAddressValidatorRegistry;

    @Mock
    private TransferProcessStore transferProcessStore;

    @Mock
    private AssetIndex assetIndex;

    @Mock
    private DataFlowStartMessage dataFlowStartMessage;

    @Mock
    private TransferProcess transferProcess;

    @Mock
    private Asset asset;

    @Mock
    private DataAddress dataAddress;

    private EDeliveryDataSourceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new EDeliveryDataSourceFactory(
                dataAddressValidatorRegistry,
                transferProcessStore,
                assetIndex
        );
    }

    @Test
    void testSupportedType() {
        String supportedType = factory.supportedType();

        assertEquals(EDeliverySchema.EDELIVERY_TYPE, supportedType);
    }

    @Test
    void testValidateRequestSuccess() {
        DataAddress sourceDataAddress = mock(DataAddress.class);
        when(dataFlowStartMessage.getSourceDataAddress()).thenReturn(sourceDataAddress);
        when(dataAddressValidatorRegistry.validateSource(sourceDataAddress))
                .thenReturn(ValidationResult.success());

        Result<Void> result = factory.validateRequest(dataFlowStartMessage);

        assertTrue(result.succeeded());
        verify(dataAddressValidatorRegistry, times(1)).validateSource(sourceDataAddress);
    }

    @Test
    void testValidateRequestWithNullSourceDataAddress() {
        when(dataFlowStartMessage.getSourceDataAddress()).thenReturn(null);

        assertThrows(Exception.class, () -> factory.validateRequest(dataFlowStartMessage));
    }

    @Test
    void testValidateRequestFailure() {
        DataAddress sourceDataAddress = mock(DataAddress.class);
        when(dataFlowStartMessage.getSourceDataAddress()).thenReturn(sourceDataAddress);
        when(dataAddressValidatorRegistry.validateSource(sourceDataAddress))
                .thenReturn(ValidationResult.failure(Violation.violation("Invalid source data address", "")));

        Result<Void> result = factory.validateRequest(dataFlowStartMessage);

        assertTrue(result.failed());
        verify(dataAddressValidatorRegistry, times(1)).validateSource(sourceDataAddress);
    }

    @Test
    void testCreateSourceSuccess() {
        String transferProcessId = "transfer-123";
        String assetId = "asset-456";
        String triggerApi = "http://api.example.com/trigger";
        String appDataIdentifier = "app-data-789";

        when(dataFlowStartMessage.getProcessId()).thenReturn(transferProcessId);
        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);
        when(transferProcess.getAssetId()).thenReturn(assetId);
        when(assetIndex.findById(assetId)).thenReturn(asset);
        when(asset.getDataAddress()).thenReturn(dataAddress);
        when(dataAddress.getType()).thenReturn(EDeliverySchema.EDELIVERY_TYPE);
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API)).thenReturn(triggerApi);
        when(dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER)).thenReturn(appDataIdentifier);

        EDeliveryDataSource source = (EDeliveryDataSource) factory.createSource(dataFlowStartMessage);

        assertNotNull(source);
        assertEquals(transferProcessId, source.getTransferProcessId());
        assertEquals(triggerApi, source.getEDeliveryTriggerApi());
        assertEquals(appDataIdentifier, source.getAppDataIdentifier());
    }

    @Test
    void testCreateSourceTransferProcessNotFound() {
        String transferProcessId = "nonexistent-transfer";
        when(dataFlowStartMessage.getProcessId()).thenReturn(transferProcessId);
        when(transferProcessStore.findById(transferProcessId)).thenReturn(null);

        EdcException exception = assertThrows(EdcException.class, () -> factory.createSource(dataFlowStartMessage));

        assertTrue(exception.getMessage().contains("Transfer process not found"));
        assertTrue(exception.getMessage().contains(transferProcessId));
        verify(assetIndex, never()).findById(any());
    }

    @Test
    void testCreateSourceAssetNotFound() {
        String transferProcessId = "transfer-123";
        String assetId = "nonexistent-asset";

        when(dataFlowStartMessage.getProcessId()).thenReturn(transferProcessId);
        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);
        when(transferProcess.getAssetId()).thenReturn(assetId);
        when(assetIndex.findById(assetId)).thenReturn(null);

        EdcException exception = assertThrows(EdcException.class, () -> factory.createSource(dataFlowStartMessage));

        assertTrue(exception.getMessage().contains("Asset not found"));
        assertTrue(exception.getMessage().contains(assetId));
    }

    @Test
    void testCreateSourceInvalidDataAddressType() {
        String transferProcessId = "transfer-123";
        String assetId = "asset-456";
        String invalidType = "invalid-type";

        when(dataFlowStartMessage.getProcessId()).thenReturn(transferProcessId);
        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);
        when(transferProcess.getAssetId()).thenReturn(assetId);
        when(assetIndex.findById(assetId)).thenReturn(asset);
        when(asset.getDataAddress()).thenReturn(dataAddress);
        when(dataAddress.getType()).thenReturn(invalidType);

        EdcException exception = assertThrows(EdcException.class, () -> factory.createSource(dataFlowStartMessage));

        assertTrue(exception.getMessage().contains("Invalid asset data address type"));
        assertTrue(exception.getMessage().contains(invalidType));
        verify(dataAddress, never()).getStringProperty(any());
    }

    @Test
    void testCreateSourceWithNullTriggerApi() {
        String transferProcessId = "transfer-123";
        String assetId = "asset-456";
        String appDataIdentifier = "app-data-789";

        when(dataFlowStartMessage.getProcessId()).thenReturn(transferProcessId);
        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);
        when(transferProcess.getAssetId()).thenReturn(assetId);
        when(assetIndex.findById(assetId)).thenReturn(asset);
        when(asset.getDataAddress()).thenReturn(dataAddress);
        when(dataAddress.getType()).thenReturn(EDeliverySchema.EDELIVERY_TYPE);
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API)).thenReturn(null);
        when(dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER)).thenReturn(appDataIdentifier);

        EDeliveryDataSource source = (EDeliveryDataSource) factory.createSource(dataFlowStartMessage);

        assertNotNull(source);
        assertNull(source.getEDeliveryTriggerApi());
        assertEquals(appDataIdentifier, source.getAppDataIdentifier());
    }

    @Test
    void testCreateSourceWithNullAppDataIdentifier() {
        String transferProcessId = "transfer-123";
        String assetId = "asset-456";
        String triggerApi = "http://api.example.com/trigger";

        when(dataFlowStartMessage.getProcessId()).thenReturn(transferProcessId);
        when(transferProcessStore.findById(transferProcessId)).thenReturn(transferProcess);
        when(transferProcess.getAssetId()).thenReturn(assetId);
        when(assetIndex.findById(assetId)).thenReturn(asset);
        when(asset.getDataAddress()).thenReturn(dataAddress);
        when(dataAddress.getType()).thenReturn(EDeliverySchema.EDELIVERY_TYPE);
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API)).thenReturn(triggerApi);
        when(dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER)).thenReturn(null);

        EDeliveryDataSource source = (EDeliveryDataSource) factory.createSource(dataFlowStartMessage);

        assertNotNull(source);
        assertEquals(triggerApi, source.getEDeliveryTriggerApi());
        assertNull(source.getAppDataIdentifier());
    }

}