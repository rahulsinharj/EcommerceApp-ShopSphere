package com.shopsphere.inventory.service;
import com.shopsphere.inventory.domain.Inventory;
import com.shopsphere.inventory.exception.InsufficientInventoryException;
import com.shopsphere.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository repository;

    @Transactional
    public void reserveInventory(String productId, Integer quantity) {
        Inventory inventory = repository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found in inventory"));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new InsufficientInventoryException("Not enough inventory for product: " + productId);
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        repository.save(inventory);
    }
    
    @Transactional
    public void releaseInventory(String productId, Integer quantity) {
        Inventory inventory = repository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found in inventory"));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        repository.save(inventory);
    }
}
