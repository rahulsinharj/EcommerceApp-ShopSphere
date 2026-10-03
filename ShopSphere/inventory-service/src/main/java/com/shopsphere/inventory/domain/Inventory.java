package com.shopsphere.inventory.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {
    @Id
    private String productId;
    
    // We use @Version for optimistic locking to prevent overselling
    @Version
    private Long version;
    
    private Integer availableQuantity;
    private Integer reservedQuantity;
}
