package tom.burrows.alertruleservice.dto;

import tom.burrows.alertruleservice.domain.AlertRule;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;
import java.time.Instant;

public record AlertRuleResponse(
        Long id,
        Long userId,
        String symbol,
        AlertCondition condition,
        BigDecimal threshold,
        boolean active,
        Instant lastTriggeredAt,
        Instant createdAt
) {

    public static AlertRuleResponse from(AlertRule rule) {
        return new AlertRuleResponse(
                rule.getId(),
                rule.getUserId(),
                rule.getSymbol(),
                rule.getCondition(),
                rule.getThreshold(),
                rule.isActive(),
                rule.getLastTriggeredAt(),
                rule.getCreatedAt());
    }
}
