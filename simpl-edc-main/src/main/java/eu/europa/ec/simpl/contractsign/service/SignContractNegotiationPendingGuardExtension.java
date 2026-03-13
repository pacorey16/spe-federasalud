package eu.europa.ec.simpl.contractsign.service;

import eu.europa.ec.simpl.contractsign.listener.SignContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

@Provides({
        ContractNegotiationPendingGuard.class,
})
@Extension(SignContractNegotiationPendingGuardExtension.EXTENSION_NAME)
public class SignContractNegotiationPendingGuardExtension implements ServiceExtension {

    @Setting(required = true)
    static final String CONTRACT_MANAGER_EXTENSION_ENABLED = "contractmanager.extension.enabled";

    public static final String EXTENSION_NAME = "SIMPL Contract Negotation Pending Guard Extension";

    @Provider
    public ContractNegotiationPendingGuard createSignContractNegotiationPendingGuard(ServiceExtensionContext context) {
        var extensionEnabled = context.getSetting(CONTRACT_MANAGER_EXTENSION_ENABLED, "false");
        if (Boolean.parseBoolean(extensionEnabled)) {
            return new SignContractNegotiationPendingGuard();
        } else {
            return it -> false;
        }
    }
}
