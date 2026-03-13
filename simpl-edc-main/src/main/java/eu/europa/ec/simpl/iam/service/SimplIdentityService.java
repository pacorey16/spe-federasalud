package eu.europa.ec.simpl.iam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import eu.europa.ec.simpl.iam.jwt.IdentityAttribute;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;

import java.util.List;
import java.util.Objects;

@Slf4j
public class SimplIdentityService implements IdentityService {

    private final TypeManager typeManager;
    private final OkHttpClient client;
    private final IamExtension.IamExtensionContext extensionContext;

    public SimplIdentityService(final TypeManager typeManager,
                                final OkHttpClient client,
                                final IamExtension.IamExtensionContext extensionContext) {
        this.typeManager = typeManager;
        this.client = client;
        this.extensionContext = extensionContext;
    }

    @Override
    public Result<TokenRepresentation> obtainClientCredentials(TokenParameters parameters) {
        final var isGatewayDisabled = extensionContext.isGatewayDisabled();

        if(isGatewayDisabled) {
            extensionContext.getMonitor().debug("SimplIdentityService obtainClientCredentials is disabled, "
                    + "using mocked identity attributes");
        }

        final var identityAttributesFromAuthProvider = isGatewayDisabled
                ? typeManager.readValue(extensionContext.getMockedAgentIdentityAttributes(),
                new TypeReference<List<AgentIdentityAttribute>>() {})
                : getIdentityAttributesFromAuthenticationProvider();
        final var token = new Token();
        if (!identityAttributesFromAuthProvider.isEmpty()) {
            extensionContext.getMonitor().debug("SimplIdentityService::obtainClientCredentials "
                    + "-> identity attributes from auth provider = " + identityAttributesFromAuthProvider);
            
            token.setIdentityAttributes(identityAttributesFromAuthProvider
                    .stream()
                    .map(IdentityAttribute::mapAgentIdentityAttributeToIdentityAttribute)
                    .toList());
        }
        token.setAudience(parameters.getStringClaim("aud"));
        token.setRegion(extensionContext.getRegion());
        token.setClientId(extensionContext.getParticipantId());
        final var tokenAsString = typeManager.writeValueAsString(token);
        extensionContext.getMonitor().debug("SimplIdentityService::obtainClientCredentials -> token = "
                + tokenAsString);
        return Result.success(TokenRepresentation.Builder.newInstance()
                .token(tokenAsString)
                .build());
    }

    @Override
    public Result<ClaimToken> verifyJwtToken(TokenRepresentation tokenRepresentation, VerificationContext context) {
        var token = typeManager.readValue(tokenRepresentation.getToken(), Token.class);

        extensionContext.getMonitor().debug("SimplIdentityService::verifyJwtToken -> token = " + token.toString());
        return Result.success(ClaimToken.Builder.newInstance()
                .claim("region", token.region)
                .claim("client_id", token.clientId)
                .claim("identity_attributes", token.identityAttributes)
                .build());
    }

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    private static class Token {
        private String region;
        private String audience;
        private String clientId;
        private List<IdentityAttribute> identityAttributes;
    }

    @SneakyThrows
    private  List<AgentIdentityAttribute> getIdentityAttributesFromAuthenticationProvider() {

        final var url = extensionContext.getAuthenticationProviderUrl() + "/v1/agent/identityAttributes";
        final var request = new Request.Builder().get().url(url).build();
        try (final var response = client.newCall(request).execute()) {
            final var responseBody = Objects.requireNonNull(response.body()).string();
            if(!response.isSuccessful()) {
                extensionContext.getMonitor()
                        .debug("SimplIdentityService::getIdentityAttributesFromAuthenticationProvider "
                                + "-> unsuccessful call to auth provider. Returned body: " + responseBody);
            }
            return typeManager.readValue(responseBody,
                    new TypeReference<>() {});
        }
    }
}
