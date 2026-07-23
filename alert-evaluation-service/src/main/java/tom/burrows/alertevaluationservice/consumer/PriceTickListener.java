package tom.burrows.alertevaluationservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tom.burrows.alertevaluationservice.service.AlertEvaluationService;
import tom.burrows.commons.events.PriceTick;
import tom.burrows.commons.kafka.KafkaTopics;

@Component
public class PriceTickListener {

    private final AlertEvaluationService evaluationService;

    public PriceTickListener(AlertEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @KafkaListener(topics = KafkaTopics.PRICE_TICKS, groupId = "alert-evaluation-service")
    public void listen(PriceTick tick) {
        evaluationService.evaluate(tick);
    }
}
