package eu.europa.ec.simpl.contractsign.listener;

import eu.europa.ec.simpl.contractsign.event.ContractSignedEvent;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationListener;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventSubscriber;

import java.util.HashSet;
import java.util.Set;

/**
 * PendingGuard for ContractNegotiation Process to ensure process only continues after Provider and Consumer have signed the negotiation explicitly.
 */
public class SignContractNegotiationPendingGuard implements ContractNegotiationPendingGuard, EventSubscriber, ContractNegotiationListener {

    private final Set<String> signedContracts = new HashSet<>();
    /**
     * set state machine to pending until user signed contract both for consumer and provider.
     */
    @Override
    public boolean test(ContractNegotiation negotiation) {
       if (signedContracts.contains(negotiation.getId())) {
           return false;
       }
       if (negotiation.getType() == ContractNegotiation.Type.PROVIDER) {
            return ContractNegotiationStates.VERIFIED.code() == negotiation.getState();
        } else {
           return ContractNegotiationStates.VERIFYING.code() == negotiation.getState();
        }
    }

    /**
     * Listener for ContractSignedEvent to add the contract negotiation id into the white list for the pending guard.
     * @param event the event happened.
     * @param <E> the envelop for the event.
     */
    public <E extends Event> void on(EventEnvelope<E> event) {
        if (event.getPayload() instanceof ContractSignedEvent) {
            signedContracts.add(((ContractSignedEvent) event.getPayload()).getContractNegotiationId());
        }
    }

    /**
     * Listener to remove the contract negotiation id from the list as negotiation is finalized.
     * @param negotiation the contract negotiation that has been finalized.
     */
    @Override
    public void finalized(ContractNegotiation negotiation) {
        signedContracts.remove(negotiation.getId());
    }

    /**
     * Listener to remove the contract negotiation id from the list as negotiation is terminated.
     * @param negotiation the contract negotiation that has been terminated.
     */
    @Override
    public void terminated(ContractNegotiation negotiation) {
        signedContracts.remove(negotiation.getId());
    }
}
