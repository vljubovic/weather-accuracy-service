package ba.root.weather.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // Add interceptor for handling User-Agent headers
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new UserAgentInterceptor());
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }
}