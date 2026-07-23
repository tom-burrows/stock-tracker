package tom.burrows.notificationservice.dto;

import tom.burrows.commons.domain.AlertCondition;
import tom.burrows.notificationservice.domain.Notification;

import java.math.BigDecimal;
import java.time.Instant;

public record NotificationPayload(
        Long id,
        Long ruleId,
        Long userId,
        String symbol,
        AlertCondition condition,
        BigDecimal threshold,
        BigDecimal observedPrice,
        Instant triggeredAt,
        Instant createdAt
) {

    public static NotificationPayload from(Notification notification) {
        return new NotificationPayload(
                notification.getId(),
                notification.getRuleId(),
                notification.getUserId(),
                notification.getSymbol(),
                notification.getCondition(),
                notification.getThreshold(),
                notification.getObservedPrice(),
                notification.getTriggeredAt(),
                notification.getCreatedAt());
    }
}
