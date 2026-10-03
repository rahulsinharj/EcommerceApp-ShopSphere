package com.shopsphere.customer.controller;
import com.shopsphere.customer.dto.CustomerDto;
import com.shopsphere.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService service;

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerDto> getCustomer(@PathVariable String customerId) {
        return ResponseEntity.ok(service.getCustomer(customerId));
    }
}
