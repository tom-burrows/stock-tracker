package tom.burrows.priceingestionservice.ingestion;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tom.burrows.commons.events.PriceTick;
import tom.burrows.commons.kafka.KafkaTopics;

@Component
public class PriceTickProducer {

    private final KafkaTemplate<String, PriceTick> kafkaTemplate;

    public PriceTickProducer(KafkaTemplate<String, PriceTick> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PriceTick tick) {
        kafkaTemplate.send(KafkaTopics.PRICE_TICKS, tick.symbol(), tick);
    }
}
