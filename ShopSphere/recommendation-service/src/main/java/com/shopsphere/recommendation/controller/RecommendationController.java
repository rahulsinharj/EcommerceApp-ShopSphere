package com.shopsphere.recommendation.controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    @GetMapping("/{productId}")
    public Map<String, String> getRecommendations(@PathVariable String productId) throws InterruptedException {
        Thread.sleep(100); // simulate latency
        return Map.of("productId", productId, "recommendationText", "Customers also bought Y");
    }
}
