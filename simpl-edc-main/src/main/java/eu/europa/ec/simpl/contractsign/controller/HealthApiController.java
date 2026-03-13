package eu.europa.ec.simpl.contractsign.controller;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * The Health Endpoint to request state of Health service.
 *
 */
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Path("/")
public class HealthApiController {

    
    private final Monitor monitor;
    
    /**
     * Convenience Contructor to create instance of Health Service Controller.
     * 
     * @param monitor the monitor to use.
     */
    public HealthApiController(Monitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Endpoint for Health service API to request state of Health service.
     * @return status of Health Service.
     */
    @GET
    @Path("health")
    public String checkHealth() {
        monitor.info("Received a health request");
        return "{\"response\":\"I'm alive!\"}";
    }
}
