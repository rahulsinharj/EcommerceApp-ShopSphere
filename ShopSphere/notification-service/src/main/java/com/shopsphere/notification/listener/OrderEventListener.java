package com.shopsphere.notification.listener;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderEventListener {
    @KafkaListener(topics = "order.created", groupId = "notification-group")
    public void handleOrderCreated(ConsumerRecord<String, String> record) {
        String orderId = record.key();
        String payload = record.value();
        
        // Multithreading note: By default Spring Kafka uses 1 thread per listener.
        // We can increase concurrency in application.yml or offload to an Executor if IO-bound.
        log.info("[NOTIFICATION] Consumed OrderCreated event! Partition: {}, OrderId: {}, Payload: {}", 
            record.partition(), orderId, payload);
            
        // Idempotency check should happen here before processing.
    }
}
