package com.shopsphere.order.repository;
import com.shopsphere.order.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderRepository extends JpaRepository<OrderEntity, String> {}
