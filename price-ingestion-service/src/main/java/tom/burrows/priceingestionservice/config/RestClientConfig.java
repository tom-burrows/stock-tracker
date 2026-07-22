package tom.burrows.priceingestionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient coinGeckoRestClient(IngestionProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
