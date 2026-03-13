package eu.europa.ec.simpl.edelivery.datasource;

import eu.europa.ec.simpl.edelivery.utils.EDeliverySchema;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSourceFactory;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.jetbrains.annotations.NotNull;

public class EDeliveryDataSourceFactory implements DataSourceFactory {

    private final DataAddressValidatorRegistry dataAddressValidatorRegistry;
    private final TransferProcessStore transferProcessStore;
    private final AssetIndex assetIndex;

    public EDeliveryDataSourceFactory(
            DataAddressValidatorRegistry dataAddressValidatorRegistry,
            TransferProcessStore transferProcessStore,
            AssetIndex assetIndex) {
        this.dataAddressValidatorRegistry = dataAddressValidatorRegistry;
        this.transferProcessStore = transferProcessStore;
        this.assetIndex = assetIndex;
    }

    @Override
    public String supportedType() {
        return EDeliverySchema.EDELIVERY_TYPE;
    }

    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage request) {
        var source = request.getSourceDataAddress();
        return dataAddressValidatorRegistry.validateSource(source).flatMap(ValidationResult::toResult);
    }

    @Override
    public DataSource createSource(DataFlowStartMessage request) {

        var transferProcessId = request.getProcessId();

        var transferProcess = transferProcessStore.findById(transferProcessId);
        if (transferProcess == null) {
            throw new EdcException("Transfer process not found: " + transferProcessId);
        }
        var assetId = transferProcess.getAssetId();

        var asset = assetIndex.findById(assetId);
        if (asset == null) {
            throw new EdcException("Asset not found: " + assetId);
        }
        if (!asset.getDataAddress().getType().equals(EDeliverySchema.EDELIVERY_TYPE)) {
            throw new EdcException("Invalid asset data address type: " + asset.getDataAddress().getType());
        }

        var edeliveryTriggerAPI = asset.getDataAddress().getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API);
        var appDataIdentifier = asset.getDataAddress().getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER);

        return new EDeliveryDataSource(transferProcessId, edeliveryTriggerAPI, appDataIdentifier);
    }

}
