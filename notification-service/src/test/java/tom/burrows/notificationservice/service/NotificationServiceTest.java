package tom.burrows.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tom.burrows.commons.domain.AlertCondition;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.notificationservice.domain.Notification;
import tom.burrows.notificationservice.dto.NotificationPayload;
import tom.burrows.notificationservice.repository.NotificationRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NotificationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new NotificationService(repository, messagingTemplate);
    }

    @Test
    void handleSavesAndBroadcastsTheNotification() {
        AlertTriggeredEvent event = new AlertTriggeredEvent(1L, 2L, "BTC", AlertCondition.PRICE_ABOVE,
                new BigDecimal("60000"), new BigDecimal("65000"), Instant.now());

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        service.handle(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getRuleId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(2L);
        assertThat(saved.getSymbol()).isEqualTo("BTC");
        assertThat(saved.getObservedPrice()).isEqualByComparingTo("65000");

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().id()).isEqualTo(99L);
        assertThat(payloadCaptor.getValue().symbol()).isEqualTo("BTC");
    }

    @Test
    void listByUserDelegatesToRepository() {
        Notification notification = new Notification();
        when(repository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(notification));

        assertThat(service.listByUser(1L)).containsExactly(notification);
    }
}
