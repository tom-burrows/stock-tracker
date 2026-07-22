package tom.burrows.commons.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertTriggeredEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void roundTripsThroughJson() throws Exception {
        AlertTriggeredEvent original = new AlertTriggeredEvent(
                1L, 42L, "BTC", AlertCondition.PRICE_ABOVE,
                new BigDecimal("65000.00"), new BigDecimal("67890.12"),
                Instant.parse("2026-07-22T10:15:30Z"));

        String json = objectMapper.writeValueAsString(original);
        AlertTriggeredEvent deserialized = objectMapper.readValue(json, AlertTriggeredEvent.class);

        assertThat(deserialized).isEqualTo(original);
    }
}
