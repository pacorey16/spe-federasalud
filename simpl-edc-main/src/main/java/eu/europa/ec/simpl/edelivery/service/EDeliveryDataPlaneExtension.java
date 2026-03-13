package eu.europa.ec.simpl.edelivery.service;

import eu.europa.ec.simpl.edelivery.client.BackendApiClient;
import eu.europa.ec.simpl.edelivery.datasink.EDeliveryDataSinkFactory;
import eu.europa.ec.simpl.edelivery.datasource.EDeliveryDataSourceFactory;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataTransferExecutorServiceContainer;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;

@Provides(EDeliveryDataPlaneExtension.class)
@Extension(value = EDeliveryDataPlaneExtension.EXTENSION_NAME)
public class EDeliveryDataPlaneExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "SIMPL EDelivery Data Plane Extensions";

    @Inject
    private DataAddressValidatorRegistry dataAddressValidatorRegistry;

    @Inject
    private PipelineService pipelineService;

    @Inject
    private DataTransferExecutorServiceContainer executorContainer;

    @Inject
    private TypeManager typeManager;

    @Inject
    private TransferProcessStore transferProcessStore;

    @Inject
    private AssetIndex assetIndex;

    @Inject
    private OkHttpClient httpClient;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        // Backend API
        var backendAPIClient = new BackendApiClient(monitor, httpClient, typeManager.getMapper());

        // Data Plane
        var sourceFactory = new EDeliveryDataSourceFactory(dataAddressValidatorRegistry, transferProcessStore, assetIndex);
        pipelineService.registerFactory(sourceFactory);

        var sinkFactory = new EDeliveryDataSinkFactory(monitor, dataAddressValidatorRegistry, executorContainer.getExecutorService(), backendAPIClient, typeManager);
        pipelineService.registerFactory(sinkFactory);
        monitor.debug("Data Plane components initialized");

        monitor.info("EDeliveryDataPlaneExtension initialized");
    }
}
