package eu.europa.ec.simpl.edelivery.datasource;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.EdcException;

import java.io.InputStream;
import java.util.stream.Stream;

import static org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult.success;

public class EDeliveryDataSource implements DataSource, DataSource.Part {

    private final String transferProcessId;
    private final String eDeliveryTriggerApi;
    private final String appDataIdentifier;

    EDeliveryDataSource(String transferProcessId, String eDeliveryTriggerApi, String appDataIdentifier) {
        this.transferProcessId = transferProcessId;
        this.eDeliveryTriggerApi = eDeliveryTriggerApi;
        this.appDataIdentifier = appDataIdentifier;
    }

    public String getTransferProcessId() {
        return transferProcessId;
    }

    public String getEDeliveryTriggerApi() {
        return eDeliveryTriggerApi;
    }

    public String getAppDataIdentifier() {
        return appDataIdentifier;
    }


    @Override
    public StreamResult<Stream<Part>> openPartStream() {
        return success(Stream.of(this));
    }

    @Override
    public String name() {
        return appDataIdentifier;
    }

    @Override
    public InputStream openStream() {
        throw new EdcException("openStream not supported");
    }

}

