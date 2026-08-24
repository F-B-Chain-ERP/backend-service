package com.erp.backend_service.repository;

import com.erp.core.domain.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu nguyên - placeholder; hiện chỉ dùng để kiểm tra tồn tại và lấy tên. */
@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
}
