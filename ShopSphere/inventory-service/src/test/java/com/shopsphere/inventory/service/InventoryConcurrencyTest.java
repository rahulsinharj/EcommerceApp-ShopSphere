package com.shopsphere.inventory.service;
import com.shopsphere.inventory.domain.Inventory;
import com.shopsphere.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
        Inventory inventory = new Inventory();
        inventory.setProductId("PROD-100");
        inventory.setAvailableQuantity(1); // ONLY 1 ITEM LEFT
        inventory.setReservedQuantity(0);
        inventory.setVersion(0L);
        inventoryRepository.save(inventory);
    }

    @Test
    void preventOverselling_WithConcurrentRequests() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    // 10 threads try to buy the 1 remaining item
                    inventoryService.reserveInventory("PROD-100", 1);
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | RuntimeException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        
        // Assert that ONLY ONE thread succeeded
        assertEquals(1, successCount.get(), "Only one reservation should succeed");
        assertEquals(9, failureCount.get(), "Nine reservations should fail due to optimistic locking");
        
        Inventory finalInventory = inventoryRepository.findById("PROD-100").get();
        assertEquals(0, finalInventory.getAvailableQuantity(), "Available quantity must be 0");
        assertEquals(1, finalInventory.getReservedQuantity(), "Reserved quantity must be 1");
    }
}
