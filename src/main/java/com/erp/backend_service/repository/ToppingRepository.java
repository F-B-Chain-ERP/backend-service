package com.erp.backend_service.repository;

import com.erp.core.domain.Topping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ToppingRepository extends JpaRepository<Topping, UUID> {
}
