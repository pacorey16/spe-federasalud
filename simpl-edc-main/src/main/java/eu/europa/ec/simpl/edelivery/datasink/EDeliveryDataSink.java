package eu.europa.ec.simpl.edelivery.datasink;


import eu.europa.ec.simpl.edelivery.client.BackendApiClient;
import eu.europa.ec.simpl.edelivery.dto.InternalRequest;
import eu.europa.ec.simpl.edelivery.datasource.EDeliveryDataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.connector.dataplane.util.sink.ParallelSink;
import org.eclipse.edc.spi.types.TypeManager;

import java.util.List;
import java.util.Objects;

import static java.lang.String.format;

public class EDeliveryDataSink extends ParallelSink {

    private BackendApiClient backendAPIClient;
    private TypeManager typeManager;

    private String accessServiceIdentifier;
    private String requestorIdentifier;
    private String dateFrom;
    private String dateTo;
    private String format;
    private String service;
    private String action;

    EDeliveryDataSink() {}

    @Override
    protected StreamResult<Object> transferParts(List<DataSource.Part> parts) {

        for (DataSource.Part part : parts) {
            var dataSource = (EDeliveryDataSource) part;

            var eDeliveryTriggerApi = dataSource.getEDeliveryTriggerApi();
            var appDataIdentifier = dataSource.getAppDataIdentifier();
            var authToken = getAuthToken();

            InternalRequest internalRequest = new InternalRequest(
                    eDeliveryTriggerApi,
                    appDataIdentifier,
                    accessServiceIdentifier,
                    requestorIdentifier,
                    dateFrom,
                    dateTo,
                    format,
                    service,
                    action
            );

            var response = backendAPIClient.sendTriggerRequest(internalRequest, authToken);

            if (response.getStatus().equalsIgnoreCase("success")) {
                this.monitor.info(format("Successful trigger request to eDeliveryTriggerAPI %s, appDataIdentifier: %s",
                        eDeliveryTriggerApi, appDataIdentifier));
            } else {
                return StreamResult.error((format("Error sending trigger request to eDeliveryTriggerAPI %s, error: %s",
                        eDeliveryTriggerApi, response)));
            }
        }

        return StreamResult.success();
    }

    private String getAuthToken() {
        // Authentication is not used for the moment, so we return a dummy token
        return "token";
    }

    public static class Builder extends ParallelSink.Builder<Builder, EDeliveryDataSink> {

        private Builder() {
            super(new EDeliveryDataSink());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder backendAPIClient(BackendApiClient backendAPIClient) {
            sink.backendAPIClient = backendAPIClient;
            return this;
        }

        public Builder typeManager(TypeManager typeManager) {
            sink.typeManager = typeManager;
            return this;
        }

        public Builder accessServiceIdentifier(String accessServiceIdentifier) {
            sink.accessServiceIdentifier = accessServiceIdentifier;
            return this;
        }

        public Builder requestorIdentifier(String requestorIdentifier) {
            sink.requestorIdentifier = requestorIdentifier;
            return this;
        }

        public Builder dateFrom(String dateFrom) {
            sink.dateFrom = dateFrom;
            return this;
        }

        public Builder dateTo(String dateTo) {
            sink.dateTo = dateTo;
            return this;
        }

        public Builder format(String format) {
            sink.format = format;
            return this;
        }

        public Builder service(String service) {
            sink.service = service;
            return this;
        }

        public Builder action(String action) {
            sink.action = action;
            return this;
        }

        @Override
        protected void validate() {
            Objects.requireNonNull(sink.backendAPIClient, "backendAPIClient is required");
            Objects.requireNonNull(sink.typeManager, "typeManager is required");
            Objects.requireNonNull(sink.accessServiceIdentifier, "accessServiceIdentifier is required");
            Objects.requireNonNull(sink.requestorIdentifier, "requestorIdentifier is required");
        }
    }
}
