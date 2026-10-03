package com.shopsphere.order.scheduler;
import com.shopsphere.common.event.OrderCreatedEvent;
import com.shopsphere.order.domain.OutboxEvent;
import com.shopsphere.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatus("PENDING");
        for (OutboxEvent event : pendingEvents) {
            try {
                // Here we would ideally deserialize payload to Object, for demo we pass raw string or construct event
                // Using aggregateId as the Kafka partition key ensures ordered processing
                kafkaTemplate.send(event.getEventType(), event.getAggregateId(), event.getPayload());
                
                event.setStatus("PUBLISHED");
                outboxRepository.save(event);
                log.info("Published event for order {}", event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish event {}", event.getId(), e);
                // Will retry on next schedule run
            }
        }
    }
}
