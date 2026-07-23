package tom.burrows.alertevaluationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tom.burrows.alertevaluationservice.domain.AlertRule;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findBySymbolAndActiveTrue(String symbol);
}
