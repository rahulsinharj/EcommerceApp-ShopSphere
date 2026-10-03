package com.shopsphere.product.service;
import com.shopsphere.common.dto.InventoryDto;
import com.shopsphere.common.dto.RecommendationDto;
import com.shopsphere.product.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class ProductEnrichmentService {
    private final ProductService productService;
    private final Executor executor;
    private final RestClient restClient;

    public ProductEnrichmentService(ProductService productService, Executor productEnrichmentExecutor) {
        this.productService = productService;
        this.executor = productEnrichmentExecutor;
        this.restClient = RestClient.create();
    }

    public Map<String, Object> getEnrichedProductDetails(String productId) {
        // 1. Fetch Product
        ProductDto product = productService.getProduct(productId);

        // 2. Fetch Inventory and Recommendations Concurrently
        CompletableFuture<InventoryDto> inventoryFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return restClient.get()
                        .uri("http://localhost:8083/api/inventory/" + productId)
                        .retrieve()
                        .body(InventoryDto.class);
            } catch (Exception e) {
                // Partial failure handling
                InventoryDto fallback = new InventoryDto();
                fallback.setProductId(productId);
                fallback.setAvailableQuantity(-1); // UNKNOWN
                return fallback;
            }
        }, executor);

        CompletableFuture<RecommendationDto> recommendationFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return restClient.get()
                        .uri("http://localhost:8089/api/recommendations/" + productId)
                        .retrieve()
                        .body(RecommendationDto.class);
            } catch (Exception e) {
                return new RecommendationDto(); // Empty fallback
            }
        }, executor);

        // 3. Combine results
        CompletableFuture.allOf(inventoryFuture, recommendationFuture).join();

        return Map.of(
            "product", product,
            "inventory", inventoryFuture.join(),
            "recommendation", recommendationFuture.join()
        );
    }
}
