package eu.europa.ec.simpl.contractsign.listener;

import eu.europa.ec.simpl.contractsign.event.ContractSignedEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignContractNegotiationPendingGuardTest {

    private final Clock clock = Clock.systemUTC();

    SignContractNegotiationPendingGuard guard = new SignContractNegotiationPendingGuard();

    @Test
    void testShouldReturnFalseIdIsPresentInSignedContracts() {
        ContractNegotiation negotiation = mock();
        String id = UUID.randomUUID().toString();
        when(negotiation.getId()).thenReturn(id);
        when(negotiation.getState()).thenReturn(1100);

        ContractSignedEvent event = ContractSignedEvent.Builder.newInstance()
                .contractNegotiationId(id)
                .build();

        guard.on(EventEnvelope.Builder.newInstance().at(clock.millis()).payload(event).build());

        assertFalse(guard.test(negotiation));

        assertDoesNotThrow(() -> guard.finalized(negotiation));
    }

    @Test
    void testShouldReturnFalseTypeConsumerVerified() {
        ContractNegotiation negotiation = mock();
        when(negotiation.getId()).thenReturn(UUID.randomUUID().toString());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.CONSUMER);
        when(negotiation.getState()).thenReturn(1100);

        assertFalse(guard.test(negotiation));
    }

    @Test
    void testShouldReturnTrueTypeConsumerVerifying() {
        ContractNegotiation negotiation = mock();
        when(negotiation.getId()).thenReturn(UUID.randomUUID().toString());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.CONSUMER);
        when(negotiation.getState()).thenReturn(1050);

        assertTrue(guard.test(negotiation));
    }

    @Test
    void testShouldReturnTrueTypeProviderVerified() {
        ContractNegotiation negotiation = mock();
        when(negotiation.getId()).thenReturn(UUID.randomUUID().toString());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);
        when(negotiation.getState()).thenReturn(1100);

        assertTrue(guard.test(negotiation));
    }

    @Test
    void testShouldReturnFalseTypeProviderVerifying() {
        ContractNegotiation negotiation = mock();
        when(negotiation.getId()).thenReturn(UUID.randomUUID().toString());
        when(negotiation.getType()).thenReturn(ContractNegotiation.Type.PROVIDER);
        when(negotiation.getState()).thenReturn(1050);

        assertFalse(guard.test(negotiation));
    }

    @Test
    void terminateShouldWorkCorrectly() {
        ContractNegotiation negotiation = mock();
        String id = UUID.randomUUID().toString();
        when(negotiation.getId()).thenReturn(id);
        when(negotiation.getState()).thenReturn(1100);

        ContractSignedEvent event = ContractSignedEvent.Builder.newInstance()
                .contractNegotiationId(id)
                .build();

        guard.on(EventEnvelope.Builder.newInstance().at(clock.millis()).payload(event).build());

        assertFalse(guard.test(negotiation));

        assertDoesNotThrow(() -> guard.terminated(negotiation));
    }
}
