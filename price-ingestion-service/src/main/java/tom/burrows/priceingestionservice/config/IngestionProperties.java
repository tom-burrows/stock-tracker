package tom.burrows.priceingestionservice.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(
        List<SymbolConfig> symbols,
        @DefaultValue("45s") Duration pollInterval,
        @DefaultValue("usd") String currency,
        @DefaultValue("https://api.coingecko.com/api/v3") String baseUrl
) {

    public record SymbolConfig(String coinId, String symbol) {
    }
}
