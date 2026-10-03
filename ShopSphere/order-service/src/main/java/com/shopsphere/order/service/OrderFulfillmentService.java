package com.shopsphere.order.service;
import com.shopsphere.order.dto.OrderRequestDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Service
@Slf4j
public class OrderFulfillmentService {
    private final RestClient restClient;
    private final OrderService orderService; // DB and Outbox

    public OrderFulfillmentService(OrderService orderService) {
        this.restClient = RestClient.create();
        this.orderService = orderService;
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackInventoryReservation")
    @Retry(name = "inventoryService")
    public String placeOrder(OrderRequestDto request) {
        log.info("Starting dependent order flow for customer: {}", request.getCustomerId());
        
        // 1. Reserve Inventory (Protected by Circuit Breaker & Retry)
        restClient.post()
            .uri("http://localhost:8083/api/inventory/" + request.getProductId() + "/reserve")
            .body(Map.of("orderId", "tmp-123", "quantity", request.getQuantity()))
            .retrieve()
            .toBodilessEntity();

        // 2. Process Payment...
        
        // 3. Create Order (DB + Outbox)
        return orderService.createOrder(request.getCustomerId(), 100.0);
    }
    
    // Fallback method triggered if Circuit Breaker is OPEN or Retries exhausted
    public String fallbackInventoryReservation(OrderRequestDto request, Throwable t) {
        log.error("Inventory service is down. Fast failing order creation. Reason: {}", t.getMessage());
        throw new RuntimeException("Order Failed: Inventory service unavailable. Please try again later.");
    }
}
