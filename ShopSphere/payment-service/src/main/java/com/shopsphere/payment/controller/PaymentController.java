package com.shopsphere.payment.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processPayment(@RequestBody Map<String, Object> request) {
        // Simulate payment processing
        return ResponseEntity.ok(Map.of("status", "COMPLETED", "transactionId", UUID.randomUUID().toString()));
    }
}
