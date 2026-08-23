package com.erp.backend_service.repository;

import com.erp.core.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu đơn vị tính (unit). */
@Repository
public interface UnitRepository extends JpaRepository<Unit, UUID> {
}
