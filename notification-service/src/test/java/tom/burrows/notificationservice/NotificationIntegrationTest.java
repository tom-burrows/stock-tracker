package tom.burrows.notificationservice;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tom.burrows.commons.domain.AlertCondition;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.commons.kafka.KafkaTopics;
import tom.burrows.notificationservice.dto.NotificationPayload;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Publishes an AlertTriggeredEvent via a real Kafka producer, then proves the full
 * consume -> persist -> broadcast slice: (1) a row lands in notifications (Awaitility
 * against the Testcontainers Postgres instance), and (2) a real STOMP client subscribed
 * to /topic/notifications receives the broadcast, over a real WebSocket connection to
 * this test's random server port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationIntegrationTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    private KafkaTemplate<String, AlertTriggeredEvent> alertTriggeredKafkaTemplate;

    @AfterEach
    void tearDown() {
        if (alertTriggeredKafkaTemplate != null) {
            alertTriggeredKafkaTemplate.destroy();
        }
        jdbcTemplate.update("DELETE FROM notifications");
    }

    @Test
    void alertTriggeredEventIsPersistedAndBroadcastOverStomp() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        // Without this, NotificationPayload's Instant fields fail to deserialize and
        // DefaultStompSession swallows the exception via the default no-op
        // StompSessionHandlerAdapter.handleException, so the frame silently never arrives.
        converter.getObjectMapper().registerModule(new JavaTimeModule());
        stompClient.setMessageConverter(converter);

        BlockingQueue<NotificationPayload> received = new LinkedBlockingQueue<>();
        StompSession session = stompClient
                .connectAsync("http://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
                })
                .get(10, TimeUnit.SECONDS);
        session.subscribe("/topic/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return NotificationPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((NotificationPayload) payload);
            }
        });

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        ProducerFactory<String, AlertTriggeredEvent> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        alertTriggeredKafkaTemplate = new KafkaTemplate<>(producerFactory);

        // AlertTriggeredListener's consumer group uses the default auto-offset-reset=latest
        // (as production does). Without waiting for it to actually join the group and get its
        // partition assigned first, publishing here races the listener's startup: if the
        // event lands before the listener's initial offset position is resolved, "latest"
        // skips right past it and the test flakes intermittently. Only 1 partition is expected
        // here (not the 3 alert-evaluation-service's own KafkaTopicConfig creates in prod) since
        // this isolated test's throwaway Kafka container auto-creates the topic on first use
        // with the broker default of 1 partition — no NewTopic bean for alert-triggers exists
        // in this service's own context.
        MessageListenerContainer container = registry.getListenerContainer("alert-triggered-listener");
        ContainerTestUtils.waitForAssignment(container, 1);

        AlertTriggeredEvent event = new AlertTriggeredEvent(1L, 2L, "BTC", AlertCondition.PRICE_ABOVE,
                new BigDecimal("60000.00"), new BigDecimal("65000.00"), Instant.now());
        alertTriggeredKafkaTemplate.send(KafkaTopics.ALERT_TRIGGERS, event.symbol(), event);

        NotificationPayload payload = received.poll(15, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.ruleId()).isEqualTo(1L);
        assertThat(payload.userId()).isEqualTo(2L);
        assertThat(payload.symbol()).isEqualTo("BTC");
        assertThat(payload.observedPrice()).isEqualByComparingTo("65000.00");

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE user_id = 2", Integer.class);
            assertThat(count).isEqualTo(1);
        });

        session.disconnect();
    }
}
