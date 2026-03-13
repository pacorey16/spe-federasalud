package eu.europa.ec.simpl.policy.function;

import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.params.ParameterizedTest;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationConstraintFunctionTest {

    @Mock
    private Monitor monitor;

    @InjectMocks
    private LocationConstraintFunction locationConstraintFunction;

    @ParameterizedTest
    @MethodSource("provideOperatorsAndExpectedResults")
    void evaluateShouldReturnCorrectBooleanBasedOnGivenOperator(final Operator operator, final boolean expectedResult) {
        final var rightValue = "us";
        final var permission = new Permission();
        final var policyContext = mock(ContractNegotiationPolicyContext.class);
        final var participantAgent = mock(ParticipantAgent.class);

        when(policyContext.participantAgent()).thenReturn(participantAgent);
        when(participantAgent.getClaims())
                .thenReturn(Map.of("region", "eu"));

        final var result = locationConstraintFunction.evaluate(operator, rightValue, permission, policyContext);
        assertThat(result).isEqualTo(expectedResult);
        }

    private static Stream<Arguments> provideOperatorsAndExpectedResults() {
        return Stream.of(
                Arguments.of(Operator.EQ, false),
                Arguments.of(Operator.NEQ, true),
                Arguments.of(Operator.IS_A, false)
        );
    }
}
