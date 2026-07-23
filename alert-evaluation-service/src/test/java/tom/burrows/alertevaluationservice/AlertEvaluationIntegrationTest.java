package tom.burrows.alertevaluationservice;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.commons.events.PriceTick;
import tom.burrows.commons.kafka.KafkaTopics;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The alert_rules schema is owned by alert-rule-service's Flyway migration; this service
 * only reads/updates it. Flyway is disabled in the app's own application.yml, but this test
 * enables it against the copy of that migration in src/test/resources/db/migration, keeping
 * the test schema in lockstep with the real one.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.flyway.enabled=true")
class AlertEvaluationIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    private KafkaTemplate<String, PriceTick> priceTickKafkaTemplate;
    private Consumer<String, AlertTriggeredEvent> alertTriggersConsumer;

    @AfterEach
    void tearDown() {
        if (priceTickKafkaTemplate != null) {
            priceTickKafkaTemplate.destroy();
        }
        if (alertTriggersConsumer != null) {
            alertTriggersConsumer.close();
        }
        jdbcTemplate.update("DELETE FROM alert_rules");
    }

    @Test
    void matchingPriceTickTriggersAlertAndUpdatesCooldown() {
        jdbcTemplate.update("""
                INSERT INTO alert_rules (id, user_id, symbol, condition, threshold, active, created_at)
                VALUES (1, 1, 'BTC', 'PRICE_ABOVE', 60000.0000, true, now())
                """);

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        ProducerFactory<String, PriceTick> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        priceTickKafkaTemplate = new KafkaTemplate<>(producerFactory);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(), "test-group", "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "tom.burrows.commons.events");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AlertTriggeredEvent.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        alertTriggersConsumer = new DefaultKafkaConsumerFactory<String, AlertTriggeredEvent>(consumerProps).createConsumer();
        alertTriggersConsumer.subscribe(List.of(KafkaTopics.ALERT_TRIGGERS));

        // PriceTickListener's consumer group uses the default auto-offset-reset=latest (as
        // production does). Without waiting for it to actually join the group and get its
        // partition assigned first, publishing here races the listener's startup: if the
        // tick lands before the listener's initial offset position is resolved, "latest"
        // skips right past it and the test flakes intermittently. Only 1 partition is expected
        // here (not the 3 price-ingestion-service's own KafkaTopicConfig creates in prod) since
        // this isolated test's throwaway Kafka container auto-creates the topic on first use
        // with the broker default of 1 partition — no NewTopic bean for price-ticks exists in
        // this service's own context.
        MessageListenerContainer container = registry.getListenerContainer("price-tick-listener");
        ContainerTestUtils.waitForAssignment(container, 1);

        priceTickKafkaTemplate.send(KafkaTopics.PRICE_TICKS, "BTC",
                new PriceTick("BTC", new BigDecimal("65000.00"), "usd", Instant.now()));

        ConsumerRecord<String, AlertTriggeredEvent> record =
                KafkaTestUtils.getSingleRecord(alertTriggersConsumer, KafkaTopics.ALERT_TRIGGERS, Duration.ofSeconds(15));

        assertThat(record.value().ruleId()).isEqualTo(1L);
        assertThat(record.value().observedPrice()).isEqualByComparingTo("65000.00");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Boolean hasFired = jdbcTemplate.queryForObject(
                    "SELECT last_triggered_at IS NOT NULL FROM alert_rules WHERE id = 1", Boolean.class);
            assertThat(hasFired).isTrue();
        });
    }
}
