package eu.europa.ec.simpl.policy.function;

import eu.europa.ec.simpl.iam.jwt.IdentityAttribute;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumptionConstraintFunctionTest {

    @Mock
    private Monitor monitor;

    @InjectMocks
    private ConsumptionConstraintFunction consumptionConstraintFunction;

    @ParameterizedTest
    @MethodSource("provideOperatorsAndExpectedResults")
    void evaluateShouldReturnCorrectBooleanBasedOnGivenOperator(final Operator operator, final boolean expectedResult) {
        final var rightValue = "CONSUMER";
        final var permission = new Permission();
        final var policyContext = mock(ContractNegotiationPolicyContext.class);
        final var participantAgent = mock(ParticipantAgent.class);

        var identityAttribute1 = new IdentityAttribute(UUID.randomUUID().toString(), rightValue, rightValue, true, true,
                LocalDateTime.now(), LocalDateTime.now());
        var identityAttribute2 = new IdentityAttribute(UUID.randomUUID().toString(), "RESEARCHER", "RESEARCHER", true, true,
                LocalDateTime.now(), LocalDateTime.now());

        when(policyContext.participantAgent()).thenReturn(participantAgent);
        when(participantAgent.getClaims())
                .thenReturn(Map.of("identity_attributes", List.of(identityAttribute1, identityAttribute2)));

        final var result = consumptionConstraintFunction.evaluate(operator, rightValue, permission, policyContext);
        assertThat(result).isEqualTo(expectedResult);
        }

    private static Stream<Arguments> provideOperatorsAndExpectedResults() {
        return Stream.of(
                Arguments.of(Operator.EQ, true),
                Arguments.of(Operator.HAS_PART, true),
                Arguments.of(Operator.NEQ, false),
                Arguments.of(Operator.IS_A, false)
        );
    }
}
