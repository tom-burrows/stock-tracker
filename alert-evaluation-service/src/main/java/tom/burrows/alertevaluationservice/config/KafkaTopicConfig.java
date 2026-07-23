package tom.burrows.alertevaluationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import tom.burrows.commons.kafka.KafkaTopics;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic alertTriggersTopic() {
        return TopicBuilder.name(KafkaTopics.ALERT_TRIGGERS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
