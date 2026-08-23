package com.erp.backend_service.repository;

import com.erp.core.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu kho (warehouse). */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
}
