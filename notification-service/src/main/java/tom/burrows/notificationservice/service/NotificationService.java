package tom.burrows.notificationservice.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.notificationservice.domain.Notification;
import tom.burrows.notificationservice.dto.NotificationPayload;
import tom.burrows.notificationservice.repository.NotificationRepository;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository repository, SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public void handle(AlertTriggeredEvent event) {
        Notification notification = new Notification();
        notification.setRuleId(event.ruleId());
        notification.setUserId(event.userId());
        notification.setSymbol(event.symbol());
        notification.setCondition(event.condition());
        notification.setThreshold(event.threshold());
        notification.setObservedPrice(event.observedPrice());
        notification.setTriggeredAt(event.triggeredAt());

        Notification saved = repository.save(notification);
        messagingTemplate.convertAndSend("/topic/notifications", NotificationPayload.from(saved));
    }

    @Transactional(readOnly = true)
    public List<Notification> listByUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
