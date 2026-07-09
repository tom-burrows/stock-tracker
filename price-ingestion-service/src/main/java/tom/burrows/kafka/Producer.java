package tom.burrows.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tom.burrows.events.Event;

@Slf4j
@Component
@RequiredArgsConstructor
public class Producer {
    public static final String TOPIC = "test_topic";

    private final KafkaTemplate<String, Event> kafkaTemplate;

    public void publish(Event event) {
        String key = event.getKey();
        kafkaTemplate.send(TOPIC, key, event);
        log.info("Sent message: {}", event);
    }
}
