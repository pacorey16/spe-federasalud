package eu.europa.ec.simpl.contractsign.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.event.Event;

import java.util.Objects;

/**
 * This event is emitted when an external ContractManager call backs EDC to inform a contract has been signed by a participant.
 */
@JsonDeserialize(builder = ContractSignedEvent.Builder.class)
public class ContractSignedEvent extends Event {

    private ContractSignedEvent() {
    }
    protected String contractNegotiationId;

    public String getContractNegotiationId() {
        return contractNegotiationId;
    }

    @Override
    public String name() {
        return "contract.signed";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        ContractSignedEvent event;

        private Builder(ContractSignedEvent event) {
            this.event = event;
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder(new ContractSignedEvent());
        }

        public Builder contractNegotiationId(String contractNegotiationId) {
            event.contractNegotiationId = contractNegotiationId;
            return this;
        }

        public ContractSignedEvent build() {
            Objects.requireNonNull(event.contractNegotiationId);
            return event;
        }

    }
}
