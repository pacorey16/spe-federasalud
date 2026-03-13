package eu.europa.ec.simpl.contractsign.controller;

import eu.europa.ec.simpl.contractsign.event.ContractSignedEvent;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;

import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.monitor.Monitor;

import java.time.Clock;

import static java.lang.String.format;

/**
 * Webservice Endpoint controller for callback method to inform the state machine external contract manager has signed or rejected a contract negotiation.
 *
 */
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Path("/")
public class ContractSignApiController {
    // these both constants are here for backward compatibility and will be removed in the future
    public static final String SIGNED_TRUE_STATE = "true";
    public static final String SIGNED_FALSE_STATE = "false";

    public static final int STATUS_BAD_REQUEST = 400;

    public static final String SIGNED_STATE = "signed";
    public static final String REJECTED_STATE = "rejected";
    private final Monitor monitor;
    private final ContractNegotiationService service;
    private final EventRouter eventRouter;
    private final Clock clock;
    private final ContractNegotiationStore negotiationStore;

    /**
     * Convenience constructor to create instance of controller.
     * 
     * @param monitor the monitor to use.
     * @param service the service to use.
     */
    public ContractSignApiController(Monitor monitor, ContractNegotiationService service, ContractNegotiationStore negotiationStore, EventRouter eventRouter, Clock clock) {
        this.monitor = monitor;
        this.service = service;
        this.negotiationStore = negotiationStore;
        this.clock = clock;
        this.eventRouter = eventRouter;
    }

    /**
     * Endpoint for Callback method to inform the state machine external contract manager has signed or rejected a contract negotiation.
     *
     * @param contractNegotiationId the contract negotiation of interest.
     * @param signed signed if signed, rejected if rejected.
     * @return acknowledge of request
     */
    @POST
    @Path("signed/{contractNegotiationId}/{signed}")
    public Response notifyStateMachineAboutContractNegotiationBoolean(
            @PathParam("contractNegotiationId") String contractNegotiationId,
            @PathParam(SIGNED_STATE) String signedParam) {

        ContractSignStatus status;

        // For backward compatibility we will keep for a couple of sprints
        if (SIGNED_TRUE_STATE.equalsIgnoreCase(signedParam) || SIGNED_STATE.equalsIgnoreCase(signedParam)) {
            status = ContractSignStatus.SIGNED;
        } else if (SIGNED_FALSE_STATE.equalsIgnoreCase(signedParam) || REJECTED_STATE.equalsIgnoreCase(signedParam)) {
            status = ContractSignStatus.REJECTED;
        } else {
            monitor.warning("Invalid contract status received: " + signedParam);
            return Response.status(STATUS_BAD_REQUEST, "Invalid contract status").build();
        }

        return notifyStateMachineAboutContractNegotiationStatus(contractNegotiationId, status);
    }

    public Response notifyStateMachineAboutContractNegotiationStatus(
            @PathParam("contractNegotiationId") String contractNegotiationId,
            @PathParam("status") ContractSignStatus status) {

        monitor.info(format("%s :: Received a contract signed callback request with status %s", contractNegotiationId, status));

        // Get ContractNegotiation by ID
        ContractNegotiation negotiation = service.findbyId(contractNegotiationId);

        if (negotiation == null) {
            return buildBadRequestResponse("unknown negotiation id");
        }

        if (checkIfNegotiationStateIsValid(negotiation)) {
            return buildBadRequestResponse("negotiation has invalid negotiation state");
        }

        return processContractStatus(negotiation, status);
    }

    private Response processContractStatus(ContractNegotiation negotiation, ContractSignStatus status) {
        if (status == ContractSignStatus.REJECTED) {
            handleRejection(negotiation);
        } else if (status == ContractSignStatus.SIGNED) {
            handleSigning(negotiation);
        } else {
            return buildBadRequestResponse("Invalid contract status");
        }
        return Response.ok().build();
    }

    private void handleRejection(ContractNegotiation negotiation) {
        negotiation.setPending(false);
        negotiation.transitionTerminating("negotiation rejected from " + negotiation.getType().toString());
        negotiationStore.save(negotiation);
    }

    private void handleSigning(ContractNegotiation negotiation) {
        if ((checkCurrentState(ContractNegotiationStates.VERIFIED, negotiation.getState())
                && negotiation.getType() == ContractNegotiation.Type.PROVIDER)
                || checkCurrentState(ContractNegotiationStates.VERIFYING, negotiation.getState())) {
            publishContractSignedEvent(negotiation);
        }
    }

    private Response buildBadRequestResponse(String message) {
        monitor.warning(message);
        return Response.status(STATUS_BAD_REQUEST, message).build();
    }

    private void publishContractSignedEvent(ContractNegotiation negotiation) {
        var event = ContractSignedEvent.Builder.newInstance()
                .contractNegotiationId(negotiation.getId())
                .build();
        publish(event);
        negotiation.setPending(false);
        negotiationStore.save(negotiation);
    }

    public enum ContractSignStatus {
        SIGNED, REJECTED
    }

    private static boolean checkIfNegotiationStateIsValid(ContractNegotiation negotiation) {
        return !(checkCurrentState(ContractNegotiationStates.VERIFIED, negotiation.getState())
                || checkCurrentState(ContractNegotiationStates.VERIFYING, negotiation.getState()));
    }

    private static boolean checkCurrentState(ContractNegotiationStates state, int currentState) {
        return state == ContractNegotiationStates.from(currentState);
    }

    private void publish(ContractSignedEvent event) {
        var envelope = EventEnvelope.Builder.newInstance()
                .payload(event)
                .at(clock.millis())
                .build();
        eventRouter.publish(envelope);
    }
}
