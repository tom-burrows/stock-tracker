package tom.burrows.alertevaluationservice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "evaluation")
public record EvaluationProperties(@DefaultValue("15m") Duration cooldown) {
}
