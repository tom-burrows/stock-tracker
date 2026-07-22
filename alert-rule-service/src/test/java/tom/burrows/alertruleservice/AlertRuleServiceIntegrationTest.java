package tom.burrows.alertruleservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tom.burrows.alertruleservice.dto.AlertRuleResponse;
import tom.burrows.alertruleservice.dto.CreateAlertRuleRequest;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class AlertRuleServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createListToggleAndDeleteRoundTrip() {
        CreateAlertRuleRequest createRequest = new CreateAlertRuleRequest(1L, "BTC", AlertCondition.PRICE_ABOVE, new BigDecimal("65000"));

        ResponseEntity<AlertRuleResponse> createResponse =
                restTemplate.postForEntity("/api/alert-rules", createRequest, AlertRuleResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = createResponse.getBody().id();
        assertThat(createResponse.getBody().active()).isTrue();

        ResponseEntity<AlertRuleResponse[]> listResponse =
                restTemplate.getForEntity("/api/alert-rules?userId=1", AlertRuleResponse[].class);
        assertThat(List.of(listResponse.getBody())).extracting(AlertRuleResponse::id).contains(id);

        ResponseEntity<AlertRuleResponse> toggleResponse = restTemplate.exchange(
                "/api/alert-rules/" + id + "/toggle-active", org.springframework.http.HttpMethod.PATCH,
                null, AlertRuleResponse.class);
        assertThat(toggleResponse.getBody().active()).isFalse();

        restTemplate.delete("/api/alert-rules/" + id);

        ResponseEntity<AlertRuleResponse[]> afterDelete =
                restTemplate.getForEntity("/api/alert-rules?userId=1", AlertRuleResponse[].class);
        assertThat(List.of(afterDelete.getBody())).extracting(AlertRuleResponse::id).doesNotContain(id);
    }
}
