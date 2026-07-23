package tom.burrows.alertevaluationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tom.burrows.alertevaluationservice.config.EvaluationProperties;
import tom.burrows.alertevaluationservice.domain.AlertRule;
import tom.burrows.alertevaluationservice.producer.AlertTriggeredProducer;
import tom.burrows.alertevaluationservice.repository.AlertRuleRepository;
import tom.burrows.commons.domain.AlertCondition;
import tom.burrows.commons.events.AlertTriggeredEvent;
import tom.burrows.commons.events.PriceTick;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceTest {

    @Mock
    private AlertRuleRepository repository;

    @Mock
    private AlertTriggeredProducer producer;

    private final EvaluationProperties properties = new EvaluationProperties(Duration.ofMinutes(15));

    private AlertEvaluationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AlertEvaluationService(repository, producer, properties);
    }

    private AlertRule ruleAbove(BigDecimal threshold, Instant lastTriggeredAt) {
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setUserId(1L);
        rule.setSymbol("BTC");
        rule.setCondition(AlertCondition.PRICE_ABOVE);
        rule.setThreshold(threshold);
        rule.setActive(true);
        rule.setLastTriggeredAt(lastTriggeredAt);
        return rule;
    }

    @Test
    void firesOnMatchingPriceAbove() {
        AlertRule rule = ruleAbove(new BigDecimal("60000"), null);
        when(repository.findBySymbolAndActiveTrue("BTC")).thenReturn(List.of(rule));

        service.evaluate(new PriceTick("BTC", new BigDecimal("65000"), "usd", Instant.now()));

        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(producer).publish(captor.capture());
        assertThat(captor.getValue().ruleId()).isEqualTo(1L);
        assertThat(captor.getValue().observedPrice()).isEqualByComparingTo("65000");
        assertThat(rule.getLastTriggeredAt()).isNotNull();
        verify(repository).save(rule);
    }

    @Test
    void doesNotFireOnNonMatchingPrice() {
        AlertRule rule = ruleAbove(new BigDecimal("60000"), null);
        when(repository.findBySymbolAndActiveTrue("BTC")).thenReturn(List.of(rule));

        service.evaluate(new PriceTick("BTC", new BigDecimal("50000"), "usd", Instant.now()));

        verify(producer, never()).publish(any());
        verify(repository, never()).save(any());
    }

    @Test
    void ruleInCooldownIsNotRefiredEvenIfPriceStillMatches() {
        AlertRule rule = ruleAbove(new BigDecimal("60000"), Instant.now().minus(Duration.ofMinutes(5)));
        when(repository.findBySymbolAndActiveTrue("BTC")).thenReturn(List.of(rule));

        service.evaluate(new PriceTick("BTC", new BigDecimal("65000"), "usd", Instant.now()));

        verify(producer, never()).publish(any());
        verify(repository, never()).save(any());
    }

    @Test
    void ruleThatHasExitedCooldownFiresAgain() {
        AlertRule rule = ruleAbove(new BigDecimal("60000"), Instant.now().minus(Duration.ofMinutes(20)));
        when(repository.findBySymbolAndActiveTrue("BTC")).thenReturn(List.of(rule));

        service.evaluate(new PriceTick("BTC", new BigDecimal("65000"), "usd", Instant.now()));

        verify(producer).publish(any());
        verify(repository).save(rule);
    }

    @Test
    void noActiveRulesMeansNoProducerInteraction() {
        when(repository.findBySymbolAndActiveTrue("BTC")).thenReturn(List.of());

        service.evaluate(new PriceTick("BTC", new BigDecimal("65000"), "usd", Instant.now()));

        verifyNoInteractions(producer);
    }

    @Test
    void multipleRulesOnlyTheMatchingOneFires() {
        AlertRule matching = ruleAbove(new BigDecimal("60000"), null);
        AlertRule nonMatching = ruleAbove(new BigDecimal("70000"), null);
        nonMatching.setId(2L);
        when(repository.findBySymbolAndActiveTrue("BTC")).thenReturn(List.of(matching, nonMatching));

        service.evaluate(new PriceTick("BTC", new BigDecimal("65000"), "usd", Instant.now()));

        ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(producer).publish(captor.capture());
        assertThat(captor.getValue().ruleId()).isEqualTo(1L);
    }
}
