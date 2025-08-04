package ba.root.weather.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class UserAgentInterceptor implements ClientHttpRequestInterceptor {

    private static final String USER_AGENT = "WeatherComparisonApp/0.1 vljubovic@gmail.com";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        // Check if the request is for YR.NO API
        if (request.getURI().getHost().contains("api.met.no")) {
            // Add User-Agent header for YR.NO
            request.getHeaders().set("User-Agent", USER_AGENT);
        }

        return execution.execute(request, body);
    }
}