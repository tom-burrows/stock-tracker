package tom.burrows.alertruleservice.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tom.burrows.alertruleservice.domain.AlertRule;
import tom.burrows.alertruleservice.dto.CreateAlertRuleRequest;
import tom.burrows.alertruleservice.service.AlertRuleNotFoundException;
import tom.burrows.alertruleservice.service.AlertRuleService;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertRuleController.class)
class AlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Constructed directly rather than autowired: Boot 4's autoconfigured ObjectMapper bean
    // is the Jackson 3 (tools.jackson.*) type, not this com.fasterxml.jackson.databind one.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AlertRuleService service;

    private AlertRule ruleWith(Long id) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setUserId(1L);
        rule.setSymbol("BTC");
        rule.setCondition(AlertCondition.PRICE_ABOVE);
        rule.setThreshold(new BigDecimal("65000"));
        rule.setActive(true);
        return rule;
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest(1L, "BTC", AlertCondition.PRICE_ABOVE, new BigDecimal("65000"));
        when(service.create(any())).thenReturn(ruleWith(1L));

        mockMvc.perform(post("/api/alert-rules")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.symbol").value("BTC"));
    }

    @Test
    void createReturns400OnInvalidRequest() throws Exception {
        CreateAlertRuleRequest invalid = new CreateAlertRuleRequest(null, "", null, new BigDecimal("-5"));

        mockMvc.perform(post("/api/alert-rules")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listByUserReturnsRules() throws Exception {
        when(service.listByUser(1L)).thenReturn(java.util.List.of(ruleWith(1L)));

        mockMvc.perform(get("/api/alert-rules").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void deleteReturns404WhenRuleMissing() throws Exception {
        doThrow(new AlertRuleNotFoundException(99L)).when(service).delete(99L);

        mockMvc.perform(delete("/api/alert-rules/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204WhenRuleExists() throws Exception {
        mockMvc.perform(delete("/api/alert-rules/1"))
                .andExpect(status().isNoContent());
    }
}
