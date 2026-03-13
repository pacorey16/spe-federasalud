package eu.europa.ec.simpl.iam.service;

import eu.europa.ec.simpl.iam.AgentIdentityAttributesUtil;
import eu.europa.ec.simpl.iam.jwt.IdentityAttribute;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimplIdentityServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OkHttpClient client;

    @Mock
    private TokenParameters tokenParameters;

    @Test
    void testObtainClientCredentialsShouldReturnValidTokenRepresentationFromAuthenticationProvider() throws IOException {
        SimplIdentityService simplIdentityService = new SimplIdentityService(new JacksonTypeManager(), client,
                produceValidIamExtensionContextWithEnabledGateway());
        final var responseMock = mock(Response.class, Answers.RETURNS_DEEP_STUBS);
        when(client.newCall(any(Request.class)).execute())
                .thenReturn(responseMock);
        when(Objects.requireNonNull(responseMock.body()).string())
                .thenReturn(AgentIdentityAttributesUtil.getValidAgentIdentityAttributes());

        final var tokenRepresentationResult = simplIdentityService.obtainClientCredentials(tokenParameters).getContent();

        assertThat(tokenRepresentationResult.getToken())
                .as("token should not be null")
                .isNotNull()
                .contains("participantId")
                .contains("\"name\":\"CONSUMER\"");
    }

    @Test
    void testObtainClientCredentialsShouldReturnValidTokenRepresentationFromAuthenticationProviderWhenResponseIsEmpty()
            throws IOException {
        SimplIdentityService simplIdentityService = new SimplIdentityService(new JacksonTypeManager(), client,
                produceValidIamExtensionContextWithEnabledGateway());

        final var responseMock = mock(Response.class, Answers.RETURNS_DEEP_STUBS);
        when(client.newCall(any(Request.class)).execute())
                .thenReturn(responseMock);
        when(Objects.requireNonNull(responseMock.body()).string())
                .thenReturn(AgentIdentityAttributesUtil.getValidEmptyAgentIdentityAttributes());

        final var tokenRepresentationResult = simplIdentityService.obtainClientCredentials(tokenParameters).getContent();

        assertThat(tokenRepresentationResult.getToken())
                .as("token should not be null")
                .isNotNull()
                .contains("participantId")
                .doesNotContain("\"name\":\"CONSUMER\"");
    }

    @Test
    void testObtainClientCredentialsShouldReturnValidTokenIfGatewayIsDisabled() {
        SimplIdentityService simplIdentityService = new SimplIdentityService(new JacksonTypeManager(),
                client,
                produceValidIamExtensionContextWithDisabledGateway());

        final var tokenRepresentationResult = simplIdentityService.obtainClientCredentials(tokenParameters).getContent();

        assertThat(tokenRepresentationResult.getToken())
                .as("token should not be null")
                .isNotNull()
                .contains("participantId")
                .contains("\"name\":\"CONSUMER\"");
        verify(client, never()).newCall(any());
    }

    @Test
    void testVerifyJwtTokenShouldReturnValidClaimToken() throws IOException {
        SimplIdentityService simplIdentityService = new SimplIdentityService(new JacksonTypeManager(),
                client,
                produceValidIamExtensionContextWithEnabledGateway());

        final var responseMock = mock(Response.class, Answers.RETURNS_DEEP_STUBS);
        when(client.newCall(any(Request.class)).execute())
                .thenReturn(responseMock);
        when(Objects.requireNonNull(responseMock.body()).string())
                .thenReturn(AgentIdentityAttributesUtil.getValidAgentIdentityAttributes());

        final var tokenRepresentationResult = simplIdentityService.obtainClientCredentials(tokenParameters).getContent();

        final var claimTokenResult = simplIdentityService.verifyJwtToken(tokenRepresentationResult, null);

        final var claims = claimTokenResult.getContent().getClaims();
        assertThat(claims)
                .as("claims should have 3 entries")
                .hasSize(3);
        assertThat(claims.get("region"))
                .as("claim region should be region")
                .isEqualTo("region");
        assertThat(claims.get("client_id"))
                .as("claim client_id should be participantId")
                .isEqualTo("participantId");
        assertThat((List<IdentityAttribute>)claims.get("identity_attributes"))
                .as("claim identity_attributes should have 4 entries")
                .hasSize(4);
    }

    private IamExtension.IamExtensionContext produceValidIamExtensionContextWithDisabledGateway() {
        return IamExtension.IamExtensionContext.builder()
                .region("region")
                .participantId("participantId")
                .isGatewayDisabled(true)
                .authenticationProviderUrl("")
                .mockedAgentIdentityAttributes(AgentIdentityAttributesUtil.getValidAgentIdentityAttributes())
                .monitor(mock(Monitor.class))
                .build();
    }

    private IamExtension.IamExtensionContext produceValidIamExtensionContextWithEnabledGateway() {
        return IamExtension.IamExtensionContext.builder()
                .region("region")
                .participantId("participantId")
                .authenticationProviderUrl("http://localhost:8080")
                .isGatewayDisabled(false)
                .mockedAgentIdentityAttributes("")
                .monitor(mock(Monitor.class))
                .build();
    }
}