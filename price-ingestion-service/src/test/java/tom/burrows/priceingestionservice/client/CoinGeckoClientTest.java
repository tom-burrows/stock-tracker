package tom.burrows.priceingestionservice.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoinGeckoClientTest {

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    @Test
    void fetchesAndParsesPrices() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CoinGeckoClient client = new CoinGeckoClient(builder.build());

        server.expect(requestTo(BASE_URL + "/simple/price?ids=bitcoin,ethereum&vs_currencies=usd"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"bitcoin\":{\"usd\":67890.12},\"ethereum\":{\"usd\":3456.78}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Map<String, BigDecimal>> prices = client.fetchPrices(List.of("bitcoin", "ethereum"), "usd");

        assertThat(prices.get("bitcoin").get("usd")).isEqualByComparingTo("67890.12");
        assertThat(prices.get("ethereum").get("usd")).isEqualByComparingTo("3456.78");
        server.verify();
    }

    @Test
    void returnsEmptyMapOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CoinGeckoClient client = new CoinGeckoClient(builder.build());

        server.expect(requestTo(BASE_URL + "/simple/price?ids=bitcoin&vs_currencies=usd"))
                .andRespond(withServerError());

        Map<String, Map<String, BigDecimal>> prices = client.fetchPrices(List.of("bitcoin"), "usd");

        assertThat(prices).isEmpty();
    }
}
