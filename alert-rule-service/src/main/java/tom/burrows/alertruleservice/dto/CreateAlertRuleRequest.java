package tom.burrows.alertruleservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;

public record CreateAlertRuleRequest(
        @NotNull Long userId,
        @NotBlank String symbol,
        @NotNull AlertCondition condition,
        @NotNull @Positive BigDecimal threshold
) {
}
