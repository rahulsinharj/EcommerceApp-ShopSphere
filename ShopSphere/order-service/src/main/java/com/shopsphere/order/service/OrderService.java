package com.shopsphere.order.service;
import com.shopsphere.order.domain.OrderEntity;
import com.shopsphere.order.domain.OutboxEvent;
import com.shopsphere.order.repository.OrderRepository;
import com.shopsphere.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    @Transactional
    public String createOrder(String customerId, Double amount) {
        String orderId = UUID.randomUUID().toString();
        
        // 1. Save Order
        OrderEntity order = new OrderEntity(orderId, customerId, amount, "CREATED");
        orderRepository.save(order);
        
        // 2. Save Outbox Event in the SAME transaction
        // We use simple JSON stringification for the payload here
        String payload = String.format("{\"orderId\":\"%s\",\"customerId\":\"%s\",\"totalAmount\":%s}", orderId, customerId, amount);
        
        OutboxEvent event = OutboxEvent.builder()
            .aggregateId(orderId)
            .aggregateType("Order")
            .eventType("order.created")
            .payload(payload)
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();
            
        outboxRepository.save(event);
        
        // Transaction commits both atomic updates to PostgreSQL
        return orderId;
    }
}
