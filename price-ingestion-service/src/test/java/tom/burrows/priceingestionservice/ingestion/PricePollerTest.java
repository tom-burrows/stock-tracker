package tom.burrows.priceingestionservice.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tom.burrows.commons.events.PriceTick;
import tom.burrows.priceingestionservice.client.CoinGeckoClient;
import tom.burrows.priceingestionservice.config.IngestionProperties;
import tom.burrows.priceingestionservice.config.IngestionProperties.SymbolConfig;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricePollerTest {

    @Mock
    private CoinGeckoClient coinGeckoClient;

    @Mock
    private PriceTickProducer producer;

    @Test
    void publishesATickPerConfiguredSymbol() {
        IngestionProperties properties = new IngestionProperties(
                List.of(new SymbolConfig("bitcoin", "BTC"), new SymbolConfig("ethereum", "ETH")),
                Duration.ofSeconds(45), "usd", "https://api.coingecko.com/api/v3");
        PricePoller poller = new PricePoller(coinGeckoClient, producer, properties);

        when(coinGeckoClient.fetchPrices(eq(List.of("bitcoin", "ethereum")), eq("usd")))
                .thenReturn(Map.of(
                        "bitcoin", Map.of("usd", new BigDecimal("67890.12")),
                        "ethereum", Map.of("usd", new BigDecimal("3456.78"))));

        poller.poll();

        ArgumentCaptor<PriceTick> captor = ArgumentCaptor.forClass(PriceTick.class);
        verify(producer, org.mockito.Mockito.times(2)).publish(captor.capture());

        List<PriceTick> ticks = captor.getAllValues();
        assertThat(ticks).extracting(PriceTick::symbol).containsExactly("BTC", "ETH");
        assertThat(ticks).extracting(PriceTick::price)
                .containsExactly(new BigDecimal("67890.12"), new BigDecimal("3456.78"));
    }

    @Test
    void skipsSymbolsMissingFromTheResponse() {
        IngestionProperties properties = new IngestionProperties(
                List.of(new SymbolConfig("bitcoin", "BTC"), new SymbolConfig("solana", "SOL")),
                Duration.ofSeconds(45), "usd", "https://api.coingecko.com/api/v3");
        PricePoller poller = new PricePoller(coinGeckoClient, producer, properties);

        when(coinGeckoClient.fetchPrices(any(), eq("usd")))
                .thenReturn(Map.of("bitcoin", Map.of("usd", new BigDecimal("67890.12"))));

        poller.poll();

        verify(producer).publish(any());
        verifyNoMoreInteractions(producer);
    }
}
