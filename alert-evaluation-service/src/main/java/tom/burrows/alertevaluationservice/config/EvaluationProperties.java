package tom.burrows.alertevaluationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "evaluation")
public record EvaluationProperties(@DefaultValue("15m") Duration cooldown) {
}
