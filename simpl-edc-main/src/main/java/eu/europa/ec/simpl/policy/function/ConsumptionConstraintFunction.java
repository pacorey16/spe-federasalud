package eu.europa.ec.simpl.policy.function;

import eu.europa.ec.simpl.iam.jwt.IdentityAttribute;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.List;

import static java.lang.String.format;

public class ConsumptionConstraintFunction implements AtomicConstraintRuleFunction<Permission, ContractNegotiationPolicyContext> {

    private final Monitor monitor;

    public ConsumptionConstraintFunction(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean evaluate(Operator operator, Object rightValue, Permission rule, ContractNegotiationPolicyContext context) {
        var attributes = (List<IdentityAttribute>) context.participantAgent()
                .getClaims()
                .get("identity_attributes");

        monitor.info(format("Evaluating constraint: consumption %s %s", operator, rightValue.toString()));

        var isValuePresent = attributes.stream().map(IdentityAttribute::getCode)
                .anyMatch(x -> x.equalsIgnoreCase(rightValue.toString()));

        return switch (operator) {
            case EQ, HAS_PART -> isValuePresent;
            case NEQ -> !isValuePresent;
            default -> false;
        };
    }
}
