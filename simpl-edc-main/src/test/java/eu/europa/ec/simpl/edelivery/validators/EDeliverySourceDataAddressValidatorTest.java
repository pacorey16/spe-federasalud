package eu.europa.ec.simpl.edelivery.validators;

import eu.europa.ec.simpl.edelivery.utils.EDeliverySchema;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EDeliverySourceDataAddressValidatorTest {

    @Mock
    private DataAddress dataAddress;

    private EDeliverySourceDataAddressValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EDeliverySourceDataAddressValidator();
    }

    @Test
    void testValidateWithAllRequiredPropertiesPresent() {
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH))
                .thenReturn("http://api.test/trigger");
        when(dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER_PATH))
                .thenReturn("app-data-789");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.succeeded());
    }

    @Test
    void testValidateWithMissingEDeliveryTriggerApi() {
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH))
                .thenReturn(null);

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("edeliveryTriggerAPI is required")));
    }

    @Test
    void testValidateWithBlankEDeliveryTriggerApi() {
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH))
                .thenReturn("   ");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("edeliveryTriggerAPI is required")));
    }

    @Test
    void testValidateWithEmptyEDeliveryTriggerApi() {
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH))
                .thenReturn("");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("edeliveryTriggerAPI is required")));
    }

    @Test
    void testValidateWithMissingAppDataIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH))
                .thenReturn("http://api.test/trigger");
        when(dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER_PATH))
                .thenReturn(null);

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("appDataIdentifier is required")));
    }

    @Test
    void testValidateWithBlankAppDataIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH))
                .thenReturn("http://api.test/trigger");
        when(dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER_PATH))
                .thenReturn("   ");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("appDataIdentifier is required")));
    }

    @Test
    void testValidateWithEmptyAppDataIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH))
                .thenReturn("http://api.test/trigger");
        when(dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER_PATH))
                .thenReturn("");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("appDataIdentifier is required")));
    }

}