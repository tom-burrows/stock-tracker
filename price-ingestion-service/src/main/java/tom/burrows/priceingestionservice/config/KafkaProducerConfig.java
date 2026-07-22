package tom.burrows.priceingestionservice.config;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import tom.burrows.commons.events.PriceTick;

/**
 * Spring Boot's autoconfigured KafkaTemplate bean is declared as {@code KafkaTemplate<?, ?>},
 * which doesn't satisfy autowiring for a specifically-typed {@code KafkaTemplate<String, PriceTick>}.
 * Building our own from {@link KafkaProperties} keeps all the application.yml-configured
 * producer settings (bootstrap servers, serializers, acks) while giving us a concretely-typed bean.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, PriceTick> priceTickProducerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, PriceTick> priceTickKafkaTemplate(ProducerFactory<String, PriceTick> priceTickProducerFactory) {
        return new KafkaTemplate<>(priceTickProducerFactory);
    }
}
