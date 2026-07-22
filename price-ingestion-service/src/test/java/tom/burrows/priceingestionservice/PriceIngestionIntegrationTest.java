package tom.burrows.priceingestionservice;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tom.burrows.commons.events.PriceTick;
import tom.burrows.commons.kafka.KafkaTopics;
import tom.burrows.priceingestionservice.client.CoinGeckoClient;
import tom.burrows.priceingestionservice.ingestion.PricePoller;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class PriceIngestionIntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean
    private CoinGeckoClient coinGeckoClient;

    @Autowired
    private PricePoller pricePoller;

    private Consumer<String, PriceTick> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void publishesPriceTickThatRoundTripsThroughKafka() {
        when(coinGeckoClient.fetchPrices(any(), eq("usd")))
                .thenReturn(Map.of("bitcoin", Map.of("usd", new BigDecimal("67890.12"))));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(), "test-group", "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "tom.burrows.commons.events");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PriceTick.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        consumer = new DefaultKafkaConsumerFactory<String, PriceTick>(consumerProps).createConsumer();
        consumer.subscribe(List.of(KafkaTopics.PRICE_TICKS));

        pricePoller.poll();

        ConsumerRecord<String, PriceTick> record =
                KafkaTestUtils.getSingleRecord(consumer, KafkaTopics.PRICE_TICKS, Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo("BTC");
        assertThat(record.value().symbol()).isEqualTo("BTC");
        assertThat(record.value().price()).isEqualByComparingTo("67890.12");
        assertThat(record.value().currency()).isEqualTo("usd");
    }
}
