package tom.burrows.alertruleservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tom.burrows.alertruleservice.domain.AlertRule;
import tom.burrows.alertruleservice.dto.CreateAlertRuleRequest;
import tom.burrows.alertruleservice.repository.AlertRuleRepository;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository repository;

    private AlertRuleService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AlertRuleService(repository);
    }

    @Test
    void createSavesAnActiveRuleWithNoCooldown() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(1L, "BTC", AlertCondition.PRICE_ABOVE, new BigDecimal("65000"));
        when(repository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRule created = service.create(request);

        assertThat(created.isActive()).isTrue();
        assertThat(created.getLastTriggeredAt()).isNull();
        assertThat(created.getUserId()).isEqualTo(1L);
        assertThat(created.getSymbol()).isEqualTo("BTC");
        assertThat(created.getThreshold()).isEqualByComparingTo("65000");
    }

    @Test
    void listByUserDelegatesToRepository() {
        AlertRule rule = new AlertRule();
        when(repository.findByUserId(1L)).thenReturn(List.of(rule));

        assertThat(service.listByUser(1L)).containsExactly(rule);
    }

    @Test
    void deleteThrowsWhenRuleDoesNotExist() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(AlertRuleNotFoundException.class);
    }

    @Test
    void deleteRemovesExistingRule() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void toggleActiveFlipsTheFlag() {
        AlertRule rule = new AlertRule();
        rule.setActive(true);
        when(repository.findById(1L)).thenReturn(Optional.of(rule));

        AlertRule toggled = service.toggleActive(1L);

        assertThat(toggled.isActive()).isFalse();
    }

    @Test
    void toggleActiveThrowsWhenRuleDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActive(99L)).isInstanceOf(AlertRuleNotFoundException.class);
    }
}
