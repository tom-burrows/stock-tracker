package tom.burrows.alertevaluationservice.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.commons.kafka.KafkaTopics;

@Component
public class AlertTriggeredProducer {

    private final KafkaTemplate<String, AlertTriggeredEvent> kafkaTemplate;

    public AlertTriggeredProducer(KafkaTemplate<String, AlertTriggeredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(AlertTriggeredEvent event) {
        kafkaTemplate.send(KafkaTopics.ALERT_TRIGGERS, event.symbol(), event);
    }
}
