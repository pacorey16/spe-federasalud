package eu.europa.ec.simpl.contractsign.service;

import eu.europa.ec.simpl.contractsign.listener.SignContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationListener;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.web.spi.WebService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.HashMap;

import static eu.europa.ec.simpl.contractsign.service.ContractSignCallbackEndpointExtension.CONTRACT_MANAGER_APIKEY;
import static eu.europa.ec.simpl.contractsign.service.ContractSignCallbackEndpointExtension.CONTRACT_MANAGER_URL;
import static eu.europa.ec.simpl.contractsign.service.ContractSignCallbackEndpointExtension.CONTRACT_MANAGER_EXTENSION_ENABLED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractSignCallbackEndpointExtensionTest {

    @Mock
    WebService webService;

    @Mock
    ContractNegotiationService service;

    @Mock
    EdcHttpClient httpClient;

    @Mock
    Hostname hostname;

    @Mock
    ContractNegotiationStore negotiationStore;

    @Mock(extraInterfaces = {ContractNegotiationListener.class})
    SignContractNegotiationPendingGuard pendingGuard;

    @Mock
    ContractNegotiationObservable contractNegotiationObservable;

    @Mock
    private Clock clock;

    @Mock
    private EventRouter eventRouter;

    @Mock
    private Monitor monitor;

    @InjectMocks
    private ContractSignCallbackEndpointExtension contractSignCallbackEndpointExtension;

    @Test
    void initializeShouldBeSuccessfulExtensionEnabled() {
        final var context = mock(ServiceExtensionContext.class);
        when(context.getSetting(CONTRACT_MANAGER_EXTENSION_ENABLED, "false")).thenReturn("true");
        when(context.getMonitor())
                .thenReturn(monitor);

        var config = new HashMap<String, String>();
        config.put(CONTRACT_MANAGER_URL, "http://localhost:8080");
        config.put(CONTRACT_MANAGER_APIKEY, "gfhfj74949");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        assertDoesNotThrow(() -> contractSignCallbackEndpointExtension.initialize(context));
    }

    @Test
    void initializeShouldBeSuccessfulExtensionDisabled() {
        final var context = mock(ServiceExtensionContext.class);
        when(context.getMonitor())
                .thenReturn(monitor);

        when(context.getSetting(CONTRACT_MANAGER_EXTENSION_ENABLED, "false")).thenReturn("false");
        assertDoesNotThrow(() -> contractSignCallbackEndpointExtension.initialize(context));
    }
}
