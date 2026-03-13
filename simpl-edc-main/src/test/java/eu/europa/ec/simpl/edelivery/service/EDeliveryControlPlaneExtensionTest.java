package eu.europa.ec.simpl.edelivery.service;

import eu.europa.ec.simpl.edelivery.validators.EDeliveryDestinationDataAddressValidator;
import eu.europa.ec.simpl.edelivery.validators.EDeliverySourceDataAddressValidator;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EDeliveryControlPlaneExtensionTest {

    @Mock
    private DataAddressValidatorRegistry dataAddressValidatorRegistry;

    @Mock
    private Monitor monitor;

    @InjectMocks
    private EDeliveryControlPlaneExtension eDeliveryControlPlaneExtension;


    @Test
    void initializeShouldBeSuccessfulExtensionEnabled() {

        final var context = mock(ServiceExtensionContext.class);

        when(context.getMonitor())
                .thenReturn(monitor);

        assertDoesNotThrow(() -> eDeliveryControlPlaneExtension.initialize(context));

        verify(dataAddressValidatorRegistry, times(1)).registerSourceValidator(eq("eDelivery"), any(EDeliverySourceDataAddressValidator.class));
        verify(dataAddressValidatorRegistry, times(1)).registerDestinationValidator(eq("eDelivery"), any(EDeliveryDestinationDataAddressValidator.class));

    }
}