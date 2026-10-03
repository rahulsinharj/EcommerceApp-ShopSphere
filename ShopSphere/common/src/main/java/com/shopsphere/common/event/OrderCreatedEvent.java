package com.shopsphere.common.event;
import lombok.Data;
@Data public class OrderCreatedEvent {
    private String orderId;
    private String customerId;
    private Double totalAmount;
    private String timestamp;
}
