package com.shopsphere.inventory.controller;
import com.shopsphere.inventory.dto.ReserveRequestDto;
import com.shopsphere.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService service;

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<Void> reserve(@PathVariable String productId, @RequestBody ReserveRequestDto request) {
        service.reserveInventory(productId, request.getQuantity());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{productId}/release")
    public ResponseEntity<Void> release(@PathVariable String productId, @RequestBody ReserveRequestDto request) {
        service.releaseInventory(productId, request.getQuantity());
        return ResponseEntity.ok().build();
    }
}
