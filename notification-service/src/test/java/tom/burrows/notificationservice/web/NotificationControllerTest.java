package tom.burrows.notificationservice.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tom.burrows.commons.domain.AlertCondition;
import tom.burrows.notificationservice.domain.Notification;
import tom.burrows.notificationservice.service.NotificationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService service;

    private Notification notificationFor(Long userId) {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setRuleId(1L);
        notification.setUserId(userId);
        notification.setSymbol("BTC");
        notification.setCondition(AlertCondition.PRICE_ABOVE);
        notification.setThreshold(new BigDecimal("65000"));
        notification.setObservedPrice(new BigDecimal("65500"));
        notification.setTriggeredAt(Instant.now());
        notification.setCreatedAt(Instant.now());
        return notification;
    }

    @Test
    void listByUserReturnsNotificationsForThatUser() throws Exception {
        when(service.listByUser(1L)).thenReturn(List.of(notificationFor(1L)));

        mockMvc.perform(get("/api/notifications").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].symbol").value("BTC"));
    }

    @Test
    void listByUserReturnsEmptyArrayWhenNoneExist() throws Exception {
        when(service.listByUser(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications").param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
