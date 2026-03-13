package eu.europa.ec.simpl.iam.service;

import okhttp3.OkHttpClient;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamExtensionTest {

    @Mock
    private TypeManager typeManager;

    @Mock
    private OkHttpClient client;

    @InjectMocks
    private IamExtension iamExtension;

    @Test
    void testInitializeShouldNotThrowException() {
        final var context = mock(ServiceExtensionContext.class);
        when(context.getSetting(anyString(), anyString()))
                .thenReturn("");

        assertThatCode(() -> iamExtension.initialize(context))
                .doesNotThrowAnyException();
    }

    @Test
    void testAudienceResolverShouldNotThrowException() {
        assertThatCode(() -> iamExtension.audienceResolver())
                .doesNotThrowAnyException();
    }
}