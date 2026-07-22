package tom.burrows.alertruleservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tom.burrows.alertruleservice.domain.AlertRule;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class AlertRuleRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AlertRuleRepository repository;

    @Test
    void findByUserIdReturnsOnlyThatUsersRules() {
        repository.save(newRule(1L, "BTC"));
        repository.save(newRule(1L, "ETH"));
        repository.save(newRule(2L, "SOL"));

        List<AlertRule> rules = repository.findByUserId(1L);

        assertThat(rules).hasSize(2).extracting(AlertRule::getSymbol).containsExactlyInAnyOrder("BTC", "ETH");
    }

    private AlertRule newRule(Long userId, String symbol) {
        AlertRule rule = new AlertRule();
        rule.setUserId(userId);
        rule.setSymbol(symbol);
        rule.setCondition(AlertCondition.PRICE_ABOVE);
        rule.setThreshold(new BigDecimal("100.0000"));
        rule.setActive(true);
        return rule;
    }
}
