package com.shopsphere.order.dto;
import lombok.Data;
@Data public class OrderRequestDto { private String customerId; private String productId; private Integer quantity; private String paymentMethod; }
