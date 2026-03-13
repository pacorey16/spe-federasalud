package eu.europa.ec.simpl.edelivery.validators;

import eu.europa.ec.simpl.edelivery.utils.EDeliverySchema;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.util.string.StringUtils;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;
import org.jetbrains.annotations.NotNull;

import static org.eclipse.edc.validator.spi.Violation.violation;

public class EDeliveryDestinationDataAddressValidator implements Validator<DataAddress> {

    @Override
    public ValidationResult validate(@NotNull DataAddress dataAddress) {

        var accessServiceIdentifier = dataAddress.getStringProperty(EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH);
        if (StringUtils.isNullOrBlank(accessServiceIdentifier)) {
            var violation = violation("accessServiceIdentifier is required", EDeliverySchema.ACCESS_SERVICE_IDENTIFIER_PATH);
            return ValidationResult.failure(violation);
        }

        var requestorIdentifier = dataAddress.getStringProperty(EDeliverySchema.REQUESTOR_IDENTIFIER_PATH);
        if (StringUtils.isNullOrBlank(requestorIdentifier)) {
            var violation = violation("requestorIdentifier is required", EDeliverySchema.REQUESTOR_IDENTIFIER_PATH);
            return ValidationResult.failure(violation);
        }

        /*
        var dateFrom = dataAddress.getStringProperty(EDeliverySchema.DATE_FROM_PATH);
        if (StringUtils.isNullOrBlank(dateFrom)) {
            var violation = violation("dateFrom is required", EDeliverySchema.DATE_FROM_PATH);
            return ValidationResult.failure(violation);
        }

        var dateTo = dataAddress.getStringProperty(EDeliverySchema.DATE_TO_PATH);
        if (StringUtils.isNullOrBlank(dateTo)) {
            var violation = violation("dateTo is required", EDeliverySchema.DATE_TO_PATH);
            return ValidationResult.failure(violation);
        }

        var format = dataAddress.getStringProperty(EDeliverySchema.FORMAT_PATH);
        if (StringUtils.isNullOrBlank(format)) {
            var violation = violation("format is required", EDeliverySchema.FORMAT_PATH);
            return ValidationResult.failure(violation);
        }

        var service = dataAddress.getStringProperty(EDeliverySchema.SERVICE_PATH);
        if (StringUtils.isNullOrBlank(service)) {
            var violation = violation("service is required", EDeliverySchema.SERVICE_PATH);
            return ValidationResult.failure(violation);
        }

        var action = dataAddress.getStringProperty(EDeliverySchema.ACTION_PATH);
        if (StringUtils.isNullOrBlank(action)) {
            var violation = violation("action is required", EDeliverySchema.ACTION_PATH);
            return ValidationResult.failure(violation);
        }

         */

        return ValidationResult.success();
    }
}
