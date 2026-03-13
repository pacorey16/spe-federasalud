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
class EDeliveryDestinationDataAddressValidatorTest {

    @Mock
    private DataAddress dataAddress;

    private EDeliveryDestinationDataAddressValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EDeliveryDestinationDataAddressValidator();
    }

    @Test
    void testValidateWithAllRequiredPropertiesPresent() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn("service-123");
        when(dataAddress.getStringProperty(EDeliverySchema.REQUESTOR_IDENTIFIER_PATH))
                .thenReturn("requestor-456");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.succeeded());
    }

    @Test
    void testValidateWithMissingAccessServiceIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn(null);

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("accessServiceIdentifier is required")));
    }

    @Test
    void testValidateWithBlankAccessServiceIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn("   ");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("accessServiceIdentifier is required")));
    }

    @Test
    void testValidateWithEmptyAccessServiceIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn("");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("accessServiceIdentifier is required")));
    }

    @Test
    void testValidateWithAccessServiceIdentifierContainsOnlySpaces() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn("\t\n");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("accessServiceIdentifier is required")));
    }

    @Test
    void testValidateWithMissingRequestorIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn("service-123");
        when(dataAddress.getStringProperty(EDeliverySchema.REQUESTOR_IDENTIFIER_PATH))
                .thenReturn(null);

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("requestorIdentifier is required")));
    }

    @Test
    void testValidateWithBlankRequestorIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn("service-123");
        when(dataAddress.getStringProperty(EDeliverySchema.REQUESTOR_IDENTIFIER_PATH))
                .thenReturn("   ");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("requestorIdentifier is required")));
    }

    @Test
    void testValidateWithEmptyRequestorIdentifier() {
        when(dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH))
                .thenReturn("service-123");
        when(dataAddress.getStringProperty(EDeliverySchema.REQUESTOR_IDENTIFIER_PATH))
                .thenReturn("");

        ValidationResult result = validator.validate(dataAddress);

        assertTrue(result.failed());
        assertTrue(result.getFailureMessages().stream()
                .anyMatch(msg -> msg.contains("requestorIdentifier is required")));
    }

}