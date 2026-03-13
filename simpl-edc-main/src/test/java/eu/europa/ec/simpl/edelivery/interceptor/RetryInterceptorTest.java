package eu.europa.ec.simpl.edelivery.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryInterceptorTest {

    private RetryInterceptor retryInterceptor;

    @Mock
    private Interceptor.Chain chain;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private ResponseBody responseBody;

    @BeforeEach
    void setUp() {
        retryInterceptor = RetryInterceptor.builder()
                .maxRetryTime(3000)
                .retryDelay(1000)
                .build();
    }

    @Test
    void testSuccessfulResponseOnFirstAttempt() throws IOException {
        when(chain.request()).thenReturn(request);
        when(chain.proceed(request)).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);

        Response result = retryInterceptor.intercept(chain);

        assertNotNull(result);
        assertTrue(result.isSuccessful());
        verify(chain, times(1)).proceed(request);
    }

    @Test
    void testSuccessfulResponseAfterRetry() throws IOException {
        when(chain.request()).thenReturn(request);
        Response failedResponse = mock(Response.class);
        when(failedResponse.isSuccessful()).thenReturn(false);
        when(failedResponse.code()).thenReturn(500);
        when(failedResponse.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Internal Server Error");

        when(chain.proceed(request))
                .thenReturn(failedResponse)
                .thenReturn(failedResponse)
                .thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);

        Response result = retryInterceptor.intercept(chain);

        assertNotNull(result);
        assertTrue(result.isSuccessful());
        verify(chain, times(3)).proceed(request);
        verify(failedResponse, times(2)).close();
    }

    @Test
    void testAllRetriesExhausted() throws IOException {
        when(chain.request()).thenReturn(request);

        Response failedResponse = mock(Response.class);
        when(failedResponse.isSuccessful()).thenReturn(false);
        when(failedResponse.code()).thenReturn(503);
        when(failedResponse.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Service Unavailable");

        when(chain.proceed(request))
                .thenReturn(failedResponse)
                .thenReturn(failedResponse)
                .thenReturn(failedResponse);

        IOException exception = assertThrows(IOException.class, () -> retryInterceptor.intercept(chain));

        assertTrue(exception.getMessage().contains("503"));
        assertTrue(exception.getMessage().contains("Service Unavailable"));
        verify(chain, atLeast(3)).proceed(request);
    }

    @Test
    void testIOExceptionThrown() throws IOException {
        when(chain.request()).thenReturn(request);

        when(chain.proceed(request)).thenThrow(new IOException("Connection timeout"));

        IOException exception = assertThrows(IOException.class, () -> retryInterceptor.intercept(chain));

        assertTrue(exception.getMessage().contains("Connection timeout"));
        verify(chain, atLeast(3)).proceed(request);
    }

    @Test
    void testConnectExceptionThrown() throws IOException {
        when(chain.request()).thenReturn(request);

        when(chain.proceed(request)).thenThrow(new ConnectException("Failed to connect to server"));

        IOException exception = assertThrows(IOException.class, () -> retryInterceptor.intercept(chain));

        assertTrue(exception.getMessage().contains("Failed to connect to server"));
        verify(chain, atLeast(3)).proceed(request);
    }

    @Test
    void testNullResponseBody() throws IOException {
        when(chain.request()).thenReturn(request);

        Response failedResponse = mock(Response.class);
        when(failedResponse.isSuccessful()).thenReturn(false);
        when(failedResponse.code()).thenReturn(400);
        when(failedResponse.body()).thenReturn(null);

        when(chain.proceed(request)).thenReturn(failedResponse);

        IOException exception = assertThrows(IOException.class, () -> retryInterceptor.intercept(chain));

        assertTrue(exception.getMessage().contains("ErrorCode: 400"));
        assertTrue(exception.getMessage().contains("No response body"));
    }

}