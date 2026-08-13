package tom.burrows.alertevaluationservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tom.burrows.alertevaluationservice.domain.AlertRule;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findBySymbolAndActiveTrue(String symbol);
}
