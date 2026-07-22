package tom.burrows.alertruleservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tom.burrows.alertruleservice.domain.AlertRule;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByUserId(Long userId);
}
