package tom.burrows.alertruleservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tom.burrows.alertruleservice.domain.AlertRule;
import tom.burrows.alertruleservice.dto.CreateAlertRuleRequest;
import tom.burrows.alertruleservice.repository.AlertRuleRepository;

import java.util.List;

@Service
public class AlertRuleService {

    private final AlertRuleRepository repository;

    public AlertRuleService(AlertRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AlertRule create(CreateAlertRuleRequest request) {
        AlertRule rule = new AlertRule();
        rule.setUserId(request.userId());
        rule.setSymbol(request.symbol());
        rule.setCondition(request.condition());
        rule.setThreshold(request.threshold());
        rule.setActive(true);
        rule.setLastTriggeredAt(null);
        return repository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<AlertRule> listByUser(Long userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new AlertRuleNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public AlertRule toggleActive(Long id) {
        AlertRule rule = repository.findById(id).orElseThrow(() -> new AlertRuleNotFoundException(id));
        rule.setActive(!rule.isActive());
        return rule;
    }
}
