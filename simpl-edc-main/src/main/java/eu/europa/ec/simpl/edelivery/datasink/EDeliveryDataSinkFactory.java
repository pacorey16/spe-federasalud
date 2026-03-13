package eu.europa.ec.simpl.edelivery.datasink;

import eu.europa.ec.simpl.edelivery.client.BackendApiClient;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

import static eu.europa.ec.simpl.edelivery.utils.EDeliverySchema.*;

public class EDeliveryDataSinkFactory implements DataSinkFactory {

    private final Monitor monitor;
    private final DataAddressValidatorRegistry dataAddressValidatorRegistry;
    private final ExecutorService executorService;
    private final BackendApiClient backendAPIClient;
    private final TypeManager typeManager;

    public EDeliveryDataSinkFactory(Monitor monitor,
                                    DataAddressValidatorRegistry dataAddressValidatorRegistry,
                                    ExecutorService executorService,
                                    BackendApiClient backendAPIClient,
                                    TypeManager typeManager) {
        this.monitor = monitor;
        this.dataAddressValidatorRegistry = dataAddressValidatorRegistry;
        this.executorService = executorService;
        this.backendAPIClient = backendAPIClient;
        this.typeManager = typeManager;
    }

    @Override
    public String supportedType() {
        return EDELIVERY_TYPE;
    }

    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage request) {
        var destination = request.getDestinationDataAddress();
        return dataAddressValidatorRegistry.validateDestination(destination).flatMap(ValidationResult::toResult);
    }

    @Override
    public DataSink createSink(DataFlowStartMessage request) {

        var destination = request.getDestinationDataAddress();

        var accessServiceIdentifier = destination.getStringProperty(ACCESS_SERVICE_IDENTIFIER_PATH);
        var requestorIdentifier = destination.getStringProperty(REQUESTOR_IDENTIFIER_PATH);
        var dateFrom = destination.getStringProperty(DATE_FROM_PATH);
        var dateTo = destination.getStringProperty(DATE_TO_PATH);
        var format = destination.getStringProperty(FORMAT_PATH);
        var service = destination.getStringProperty(SERVICE_PATH);
        var action = destination.getStringProperty(ACTION_PATH);

        return EDeliveryDataSink.Builder.newInstance()
                .monitor(monitor)
                .executorService(executorService)
                .requestId(request.getId())
                .backendAPIClient(backendAPIClient)
                .typeManager(typeManager)
                .accessServiceIdentifier(accessServiceIdentifier)
                .requestorIdentifier(requestorIdentifier)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .format(format)
                .service(service)
                .action(action)
                .build();
    }
}

