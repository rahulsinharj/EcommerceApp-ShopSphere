package com.shopsphere.inventory.dto;
import lombok.Data;

@Data
public class ReserveRequestDto {
    private String orderId;
    private Integer quantity;
}
