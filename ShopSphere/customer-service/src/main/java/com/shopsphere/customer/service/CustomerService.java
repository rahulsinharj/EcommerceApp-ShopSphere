package com.shopsphere.customer.service;
import com.shopsphere.customer.domain.Customer;
import com.shopsphere.customer.dto.CustomerDto;
import com.shopsphere.customer.exception.ResourceNotFoundException;
import com.shopsphere.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository repository;

    public CustomerDto getCustomer(String id) {
        Customer customer = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        CustomerDto dto = new CustomerDto();
        dto.setId(customer.getId());
        dto.setName(customer.getName());
        dto.setEmail(customer.getEmail());
        return dto;
    }
}
