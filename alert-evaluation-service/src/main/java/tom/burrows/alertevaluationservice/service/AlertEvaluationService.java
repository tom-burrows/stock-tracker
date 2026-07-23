package tom.burrows.alertevaluationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tom.burrows.alertevaluationservice.config.EvaluationProperties;
import tom.burrows.alertevaluationservice.domain.AlertRule;
import tom.burrows.alertevaluationservice.producer.AlertTriggeredProducer;
import tom.burrows.alertevaluationservice.repository.AlertRuleRepository;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.commons.events.PriceTick;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AlertEvaluationService {

    private final AlertRuleRepository repository;
    private final AlertTriggeredProducer producer;
    private final EvaluationProperties properties;

    public AlertEvaluationService(AlertRuleRepository repository, AlertTriggeredProducer producer,
                                   EvaluationProperties properties) {
        this.repository = repository;
        this.producer = producer;
        this.properties = properties;
    }

    @Transactional
    public void evaluate(PriceTick tick) {
        List<AlertRule> candidates = repository.findBySymbolAndActiveTrue(tick.symbol());
        Instant now = Instant.now();
        for (AlertRule rule : candidates) {
            if (isInCooldown(rule, now)) {
                continue;
            }
            if (matches(rule, tick.price())) {
                fire(rule, tick, now);
            }
        }
    }

    private boolean isInCooldown(AlertRule rule, Instant now) {
        return rule.getLastTriggeredAt() != null
                && rule.getLastTriggeredAt().plus(properties.cooldown()).isAfter(now);
    }

    private boolean matches(AlertRule rule, BigDecimal price) {
        return switch (rule.getCondition()) {
            case PRICE_ABOVE -> price.compareTo(rule.getThreshold()) > 0;
            case PRICE_BELOW -> price.compareTo(rule.getThreshold()) < 0;
        };
    }

    private void fire(AlertRule rule, PriceTick tick, Instant now) {
        rule.setLastTriggeredAt(now);
        repository.save(rule);
        producer.publish(new AlertTriggeredEvent(rule.getId(), rule.getUserId(), rule.getSymbol(),
                rule.getCondition(), rule.getThreshold(), tick.price(), now));
    }
}
