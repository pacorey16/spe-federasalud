package eu.europa.ec.simpl.iam.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

@Provides(IdentityService.class)
@Extension(value = IamExtension.EXTENSION_NAME)
@NoArgsConstructor
public class IamExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "IAM";

    @Inject
    private TypeManager typeManager;

    @Inject
    private OkHttpClient client;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        final var iamExtensionContext = IamExtensionContext.builder()
                .region(context.getSetting("edc.mock.region", "eu"))
                .participantId(context.getParticipantId())
                .isGatewayDisabled(Objects.equals(context.getSetting("client.okhttp.type", ""), "OkHttpClient"))
                .mockedAgentIdentityAttributes(new String(Base64.getDecoder()
                        .decode(context.getSetting("mocked.agent.identity.attributes", "")), StandardCharsets.UTF_8))
                .monitor(context.getMonitor())
                .authenticationProviderUrl(context.getSetting("client.authenticationprovider.url", ""))
                .build();

        context.registerService(IdentityService.class,
                new SimplIdentityService(typeManager, client, iamExtensionContext));
    }

    @Provider
    public AudienceResolver audienceResolver() {
        return (msg) -> Result.success(msg.getCounterPartyAddress());
    }

    @Builder
    @Getter
    public static class IamExtensionContext {
        private final String region;
        private final String participantId;
        private final boolean isGatewayDisabled;
        private final String mockedAgentIdentityAttributes;
        private final Monitor monitor;
        private final String authenticationProviderUrl;
    }
}
