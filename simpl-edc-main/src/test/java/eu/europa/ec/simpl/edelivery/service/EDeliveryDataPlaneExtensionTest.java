package eu.europa.ec.simpl.edelivery.service;

import eu.europa.ec.simpl.edelivery.client.BackendApiClient;
import eu.europa.ec.simpl.edelivery.datasink.EDeliveryDataSinkFactory;
import eu.europa.ec.simpl.edelivery.datasource.EDeliveryDataSourceFactory;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSourceFactory;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataTransferExecutorServiceContainer;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EDeliveryDataPlaneExtensionTest {

    @Mock
    private DataAddressValidatorRegistry dataAddressValidatorRegistry;

    @Mock
    private PipelineService pipelineService;

    @Mock
    private DataTransferExecutorServiceContainer executorContainer;

    @Mock
    private TypeManager typeManager;

    @Mock
    private TransferProcessStore transferProcessStore;

    @Mock
    private AssetIndex assetIndex;

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Monitor monitor;

    @InjectMocks
    private EDeliveryDataPlaneExtension eDeliveryDataPlaneExtension;

    @Test
    void initializeShouldBeSuccessfulExtensionEnabled() {

        final var context = mock(ServiceExtensionContext.class);

        when(context.getMonitor())
                .thenReturn(monitor);

        when(executorContainer.getExecutorService())
                .thenReturn(mock(ExecutorService.class));

        assertDoesNotThrow(() -> eDeliveryDataPlaneExtension.initialize(context));

        verify(pipelineService, times(1)).registerFactory(any(DataSourceFactory.class));
        verify(pipelineService, times(1)).registerFactory(any(DataSinkFactory.class));

    }
}
