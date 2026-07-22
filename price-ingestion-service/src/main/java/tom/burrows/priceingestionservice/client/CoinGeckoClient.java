package tom.burrows.priceingestionservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class CoinGeckoClient {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);

    private final RestClient restClient;

    public CoinGeckoClient(RestClient coinGeckoRestClient) {
        this.restClient = coinGeckoRestClient;
    }

    public Map<String, Map<String, BigDecimal>> fetchPrices(List<String> coinIds, String currency) {
        try {
            Map<String, Map<String, BigDecimal>> body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/simple/price")
                            .queryParam("ids", String.join(",", coinIds))
                            .queryParam("vs_currencies", currency)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return body != null ? body : Map.of();
        } catch (RestClientException e) {
            log.warn("Failed to fetch prices from CoinGecko: {}", e.getMessage());
            return Map.of();
        }
    }
}
