package eu.europa.ec.simpl.edelivery.validators;

import eu.europa.ec.simpl.edelivery.utils.EDeliverySchema;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.util.string.StringUtils;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;
import org.jetbrains.annotations.NotNull;

import static org.eclipse.edc.validator.spi.Violation.violation;

public class EDeliverySourceDataAddressValidator implements Validator<DataAddress> {

    @Override
    public ValidationResult validate(@NotNull DataAddress dataAddress) {

        var eDeliveryAPI = dataAddress.getStringProperty(EDeliverySchema.EDELIVERY_TRIGGER_API_PATH);
        if (StringUtils.isNullOrBlank(eDeliveryAPI)) {
            var violation = violation("edeliveryTriggerAPI is required", EDeliverySchema.EDELIVERY_TRIGGER_API_PATH);
            return ValidationResult.failure(violation);
        }

        var appDataIdentifier = dataAddress.getStringProperty(EDeliverySchema.APP_DATA_IDENTIFIER_PATH);
        if (StringUtils.isNullOrBlank(appDataIdentifier)) {
            var violation = violation("appDataIdentifier is required", EDeliverySchema.APP_DATA_IDENTIFIER_PATH);
            return ValidationResult.failure(violation);
        }

        return ValidationResult.success();
    }
}
