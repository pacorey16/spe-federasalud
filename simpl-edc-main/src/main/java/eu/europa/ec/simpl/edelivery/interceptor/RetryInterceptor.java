package eu.europa.ec.simpl.edelivery.interceptor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static java.lang.String.format;

@Data
@Builder
@AllArgsConstructor
public class RetryInterceptor implements Interceptor {

    private int maxRetryTime;
    private int retryDelay;

    @SneakyThrows
    @Override
    public Response intercept(Chain chain) {
        Request request = chain.request();
        Response response = null;
        IOException exception = new IOException("Retry calls exhausted");
        int totalRetryTime = 0;

        // Retry loop
        while (totalRetryTime < maxRetryTime) {
            try {
                response = chain.proceed(request);
                // If response is successful, return it
                if (response.isSuccessful()) {
                    return response;
                } else {
                    exception = new IOException(format("ErrorCode: %s, ErrorMessage: %s", response.code(),
                            response.body() != null ? response.body().string() : "No response body"));
                }
            } catch (IOException e) {
                exception = e;
            } finally {
                totalRetryTime += retryDelay;
                // Close the response if it's not successful to avoid resource leaks
                // For successful responses, response will be returned and closed by the caller
                if (response != null && !response.isSuccessful()) {
                    response.close();
                }
            }
            // Wait before the next retry attempt
            if (totalRetryTime < maxRetryTime) {
                Thread.sleep(retryDelay);
            }
        }
        // If all attempts failed, throw the exception
        throw exception;
    }

}