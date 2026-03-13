package eu.europa.ec.simpl.edelivery.service;

import eu.europa.ec.simpl.edelivery.validators.EDeliveryDestinationDataAddressValidator;
import eu.europa.ec.simpl.edelivery.validators.EDeliverySourceDataAddressValidator;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;

@Provides({EDeliveryControlPlaneExtension.class})
@Extension(value = EDeliveryControlPlaneExtension.EXTENSION_NAME)
public class EDeliveryControlPlaneExtension implements ServiceExtension {
    public static final String EXTENSION_NAME = "SIMPL EDelivery Control Plane Extensions";
    @Inject
    private DataAddressValidatorRegistry dataAddressValidatorRegistry;

    public String name() {
        return EXTENSION_NAME;
    }

    public void initialize(ServiceExtensionContext context) {
        Monitor monitor = context.getMonitor();
        EDeliverySourceDataAddressValidator sourceValidator = new EDeliverySourceDataAddressValidator();
        this.dataAddressValidatorRegistry.registerSourceValidator("eDelivery", sourceValidator);
        EDeliveryDestinationDataAddressValidator destinationValidator = new EDeliveryDestinationDataAddressValidator();
        this.dataAddressValidatorRegistry.registerDestinationValidator("eDelivery", destinationValidator);
        monitor.debug("DataAddress Validators initialized");
        monitor.info("EDeliveryControlPlaneExtension initialized");
    }
}