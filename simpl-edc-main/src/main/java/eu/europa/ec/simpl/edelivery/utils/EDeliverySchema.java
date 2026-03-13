package eu.europa.ec.simpl.edelivery.utils;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

public final class EDeliverySchema {

    public static final int API_MAX_RETRY_TIME = 30000;
    public static final int API_RETRY_DELAY = 3000;

    public static final String EDELIVERY_TYPE = "eDelivery";

    public static final String EDELIVERY_TRIGGER_API= "edeliveryTriggerAPI";
    public static final String ACCESS_SERVICE_IDENTIFIER= "accessServiceIdentifier";
    public static final String REQUESTOR_IDENTIFIER = "requestorIdentifier";
    public static final String APP_DATA_IDENTIFIER = "appDataIdentifier";
    public static final String DATE_FROM = "dateFrom";
    public static final String DATE_TO = "dateTo";
    public static final String FORMAT = "format";
    public static final String SERVICE = "service";
    public static final String ACTION = "action";

    public static final String EDELIVERY_TRIGGER_API_PATH = EDC_NAMESPACE + EDELIVERY_TRIGGER_API;
    public static final String ACCESS_SERVICE_IDENTIFIER_PATH = EDC_NAMESPACE + ACCESS_SERVICE_IDENTIFIER;
    public static final String REQUESTOR_IDENTIFIER_PATH = EDC_NAMESPACE + REQUESTOR_IDENTIFIER;
    public static final String APP_DATA_IDENTIFIER_PATH = EDC_NAMESPACE + APP_DATA_IDENTIFIER;
    public static final String DATE_FROM_PATH = EDC_NAMESPACE + DATE_FROM;
    public static final String DATE_TO_PATH = EDC_NAMESPACE + DATE_TO;
    public static final String FORMAT_PATH = EDC_NAMESPACE + FORMAT;
    public static final String SERVICE_PATH = EDC_NAMESPACE + SERVICE;
    public static final String ACTION_PATH = EDC_NAMESPACE + ACTION;
}
