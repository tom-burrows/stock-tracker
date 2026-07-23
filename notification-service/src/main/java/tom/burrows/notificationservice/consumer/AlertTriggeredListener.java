package tom.burrows.notificationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.commons.kafka.KafkaTopics;
import tom.burrows.notificationservice.service.NotificationService;

@Component
public class AlertTriggeredListener {

    private final NotificationService notificationService;

    public AlertTriggeredListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(id = "alert-triggered-listener", topics = KafkaTopics.ALERT_TRIGGERS, groupId = "notification-service")
    public void listen(AlertTriggeredEvent event) {
        notificationService.handle(event);
    }
}
