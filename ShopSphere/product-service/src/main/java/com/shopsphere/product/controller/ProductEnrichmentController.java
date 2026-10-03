package com.shopsphere.product.controller;
import com.shopsphere.product.service.ProductEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductEnrichmentController {
    private final ProductEnrichmentService service;

    @GetMapping("/{productId}/details")
    public ResponseEntity<Map<String, Object>> getEnrichedProduct(@PathVariable String productId) {
        return ResponseEntity.ok(service.getEnrichedProductDetails(productId));
    }
}
