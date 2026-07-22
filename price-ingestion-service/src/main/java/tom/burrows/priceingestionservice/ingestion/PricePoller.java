package tom.burrows.priceingestionservice.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tom.burrows.commons.events.PriceTick;
import tom.burrows.priceingestionservice.client.CoinGeckoClient;
import tom.burrows.priceingestionservice.config.IngestionProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class PricePoller {

    private static final Logger log = LoggerFactory.getLogger(PricePoller.class);

    private final CoinGeckoClient coinGeckoClient;
    private final PriceTickProducer producer;
    private final IngestionProperties properties;

    public PricePoller(CoinGeckoClient coinGeckoClient, PriceTickProducer producer, IngestionProperties properties) {
        this.coinGeckoClient = coinGeckoClient;
        this.producer = producer;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ingestion.poll-interval}")
    public void poll() {
        List<String> coinIds = properties.symbols().stream()
                .map(IngestionProperties.SymbolConfig::coinId)
                .toList();
        Map<String, Map<String, BigDecimal>> prices = coinGeckoClient.fetchPrices(coinIds, properties.currency());
        Instant now = Instant.now();

        for (IngestionProperties.SymbolConfig symbolConfig : properties.symbols()) {
            Map<String, BigDecimal> byCurrency = prices.get(symbolConfig.coinId());
            if (byCurrency == null) {
                log.warn("No price data returned for {} ({})", symbolConfig.symbol(), symbolConfig.coinId());
                continue;
            }
            BigDecimal price = byCurrency.get(properties.currency());
            if (price == null) {
                log.warn("No {} price returned for {} ({})", properties.currency(), symbolConfig.symbol(), symbolConfig.coinId());
                continue;
            }
            producer.publish(new PriceTick(symbolConfig.symbol(), price, properties.currency(), now));
        }
    }
}
