package eu.europa.ec.simpl.policy.service;

import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class PolicyFunctionsExtensionTest {

    @Mock
    private RuleBindingRegistry ruleBindingRegistry;

    @Mock
    private PolicyEngine policyEngine;

    @InjectMocks
    private PolicyFunctionsExtension policyFunctionsExtension;

    @Test
    void initializeShouldCallRequiredMethods() {

        final var context = mock(ServiceExtensionContext.class);

        policyFunctionsExtension.initialize(context);

        verify(ruleBindingRegistry, times(3))
                .bind(anyString(), anyString());

        verify(policyEngine, times(2)).registerFunction((Class<ContractNegotiationPolicyContext>) any(), any(), anyString(), any());
    }

}
