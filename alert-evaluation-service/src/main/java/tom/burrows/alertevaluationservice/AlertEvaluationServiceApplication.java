package tom.burrows.alertevaluationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertEvaluationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertEvaluationServiceApplication.class, args);
    }
}
