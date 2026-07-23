package tom.burrows.alertevaluationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tom.burrows.commons.domain.AlertCondition;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Owns a read/update-only JPA mapping to the same physical alert_rules table that
 * alert-rule-service writes to (schema owned there, via its Flyway migration). No
 * {@code @GeneratedValue}: this service never inserts rows, only reads active rules and
 * updates lastTriggeredAt on the ones it fires.
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
public class AlertRule {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertCondition condition;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
