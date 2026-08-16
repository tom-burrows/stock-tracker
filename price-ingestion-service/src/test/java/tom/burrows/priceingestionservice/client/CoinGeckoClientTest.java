package tom.burrows.priceingestionservice.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tom.burrows.commons.dtos.Coin;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Test
    void returnsEmptyListOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CoinGeckoClient client = new CoinGeckoClient(builder.build());

        String currency = "usd";

        server.expect(requestTo(BASE_URL + "/coins/markets?vs_currencies=usd"))
            .andRespond(withServerError());
        
        List<Coin> response = client.fetchAllCoins(currency);

        assertThat(response).isEmpty();
    }

    @Test
    void fetchesMarketDataForAllCoins() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CoinGeckoClient client = new CoinGeckoClient(builder.build());

        String currency = "usd";

        server.expect(requestTo(BASE_URL + "/coins/markets?vs_currencies=usd"))
            .andRespond(withSuccess(
                "[\n" + //
                                        "  {\n" + //
                                        "    \"id\": \"bitcoin\",\n" + //
                                        "    \"symbol\": \"btc\",\n" + //
                                        "    \"name\": \"Bitcoin\",\n" + //
                                        "    \"image\": \"https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png?1696501400\",\n" + //
                                        "    \"current_price\": 77671,\n" + //
                                        "    \"market_cap\": 1555886872303,\n" + //
                                        "    \"market_cap_rank\": 1,\n" + //±
                                        "    \"fully_diluted_valuation\": 1555914058873,\n" + //
                                        "    \"total_volume\": 33235034316,\n" + //
                                        "    \"high_24h\": 78419,\n" + //
                                        "    \"low_24h\": 76696,\n" + //
                                        "    \"price_change_24h\": -747.3360347281705,\n" + //
                                        "    \"price_change_percentage_24h\": -0.95302,\n" + //
                                        "    \"market_cap_change_24h\": -15184401491.513184,\n" + //
                                        "    \"market_cap_change_percentage_24h\": -0.9665,\n" + //
                                        "    \"circulating_supply\": 20030493,\n" + //
                                        "    \"total_supply\": 20030843,\n" + //
                                        "    \"max_supply\": 21000000,\n" + //
                                        "    \"ath\": 126080,\n" + //
                                        "    \"ath_change_percentage\": -38.39571,\n" + //
                                        "    \"ath_date\": \"2025-10-06T18:57:42.558Z\",\n" + //
                                        "    \"atl\": 67.81,\n" + //
                                        "    \"atl_change_percentage\": 114443.23093,\n" + //
                                        "    \"atl_date\": \"2013-07-06T00:00:00.000Z\",\n" + //
                                        "    \"roi\": null,\n" + //
                                        "    \"last_updated\": \"2026-05-18T12:49:21.599Z\",\n" + //
                                        "    \"market_cap_rank_with_rehypothecated\": 1,\n" + //
                                        "    \"sparkline_in_7d\": {\n" + //
                                        "      \"price\": [\n" + //
                                        "        81045.84776489827,\n" + //
                                        "        81001.73089268175,\n" + //
                                        "        80898.20817826076\n" + //
                                        "      ]\n" + //
                                        "    },\n" + //
                                        "    \"price_change_percentage_1h_in_currency\": 0.5823392426906319,\n" + //
                                        "    \"price_change_percentage_24h_in_currency\": -0.9530164743774191,\n" + //
                                        "    \"price_change_percentage_7d_in_currency\": -4.042267423689584,\n" + //
                                        "    \"price_change_percentage_14d_in_currency\": -1.5720857443461431,\n" + //
                                        "    \"price_change_percentage_30d_in_currency\": 1.9453890367549207,\n" + //
                                        "    \"price_change_percentage_200d_in_currency\": -29.452371850925864,\n" + //
                                        "    \"price_change_percentage_1y_in_currency\": -25.214612207475373\n" + //
                                        "  }\n" + //
                                        "]",
                MediaType.APPLICATION_JSON
            ));

            ArrayList<Coin> coins = client.fetchAllCoins("usd");

            assertThat(coins).isNotEmpty();
            assertThat(coins.get(0).getSymbol()).isEqualTo("btc");
            assertThat(coins.get(0).getId()).isEqualTo("bitcoin");
            assertThat(coins.get(0).getCurrentPrice()).isEqualTo(77671);
            server.verify();
    }
}
