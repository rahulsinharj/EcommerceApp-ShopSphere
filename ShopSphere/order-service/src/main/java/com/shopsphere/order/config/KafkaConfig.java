package com.shopsphere.order.config;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class KafkaConfig {
    @Bean
    public NewTopic orderCreatedTopic() {
        // 3 partitions to allow concurrent processing across up to 3 consumer instances
        return TopicBuilder.name("order.created").partitions(3).replicas(1).build();
    }
}
