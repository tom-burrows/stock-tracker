package tom.burrows.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tom.burrows.events.Event;

@Slf4j
@Component
public class Consumer {

    @KafkaListener
    public void listen(Event event) {
        log.info("Received event: {}", event);
    }
}
