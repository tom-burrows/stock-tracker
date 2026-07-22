package tom.burrows.commons.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceTick(
        String symbol,
        BigDecimal price,
        String currency,
        Instant timestamp
) {
}
