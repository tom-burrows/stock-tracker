package tom.burrows.commons.events;

import java.math.BigDecimal;
import java.time.Instant;
import tom.burrows.commons.domain.AlertCondition;

public record AlertTriggeredEvent(
        Long ruleId,
        Long userId,
        String symbol,
        AlertCondition condition,
        BigDecimal threshold,
        BigDecimal observedPrice,
        Instant triggeredAt
) {
}
