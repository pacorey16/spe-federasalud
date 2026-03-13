package eu.europa.ec.simpl.contractsign.service;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static eu.europa.ec.simpl.contractsign.service.SignContractNegotiationPendingGuardExtension.CONTRACT_MANAGER_EXTENSION_ENABLED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignContractNegotiationPendingGuardExtensionTest {

    @InjectMocks
    private SignContractNegotiationPendingGuardExtension signContractNegotiationPendingGuardExtension;

    @Test
    void initializeShouldBeSuccessfulExtensionEnabled() {
        final var context = mock(ServiceExtensionContext.class);
        when(context.getSetting(CONTRACT_MANAGER_EXTENSION_ENABLED, "false")).thenReturn("true");
        var contractNegotiationPendingGuard = signContractNegotiationPendingGuardExtension.createSignContractNegotiationPendingGuard(context);
        assertInstanceOf(ContractNegotiationPendingGuard.class, contractNegotiationPendingGuard);
    }

    @Test
    void initializeShouldBeSuccessfulExtensionDisabled() {
        final var context = mock(ServiceExtensionContext.class);
        when(context.getSetting(CONTRACT_MANAGER_EXTENSION_ENABLED, "false")).thenReturn("false");
        assertDoesNotThrow(() -> signContractNegotiationPendingGuardExtension.createSignContractNegotiationPendingGuard(context));
    }
}
