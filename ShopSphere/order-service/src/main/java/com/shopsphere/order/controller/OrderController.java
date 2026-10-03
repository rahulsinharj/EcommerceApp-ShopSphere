package com.shopsphere.order.controller;
import com.shopsphere.order.dto.OrderRequestDto;
import com.shopsphere.order.service.OrderFulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderFulfillmentService service;

    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody OrderRequestDto request) {
        return ResponseEntity.ok(service.placeOrder(request));
    }
}
