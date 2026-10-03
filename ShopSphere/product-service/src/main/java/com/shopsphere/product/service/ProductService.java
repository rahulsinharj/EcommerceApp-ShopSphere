package com.shopsphere.product.service;
import com.shopsphere.product.domain.Product;
import com.shopsphere.product.dto.ProductDto;
import com.shopsphere.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;

    // Cache-aside pattern: Checks Redis first. On miss, hits DB, then saves to Redis.
    @Cacheable(value = "products", key = "#id")
    public ProductDto getProduct(String id) {
        Product product = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setPrice(product.getPrice());
        return dto;
    }
    
    // Updates DB and forces cache update
    @CachePut(value = "products", key = "#product.id")
    public ProductDto updateProduct(ProductDto product) {
        // ... DB update logic ...
        return product;
    }
}
