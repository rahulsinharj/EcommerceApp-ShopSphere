package com.shopsphere.order.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderEntity {
    @Id
    private String id;
    private String customerId;
    private Double totalAmount;
    private String status;
}
