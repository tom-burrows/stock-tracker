package tom.burrows.commons.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTickTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void roundTripsThroughJson() throws Exception {
        PriceTick original = new PriceTick("BTC", new BigDecimal("67890.12"), "usd", Instant.parse("2026-07-22T10:15:30Z"));

        String json = objectMapper.writeValueAsString(original);
        PriceTick deserialized = objectMapper.readValue(json, PriceTick.class);

        assertThat(deserialized).isEqualTo(original);
    }
}
