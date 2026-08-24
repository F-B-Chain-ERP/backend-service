package com.erp.backend_service.repository;

import com.erp.core.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu nhà cung cấp (supplier). */
@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    /** Kiểm tra mã nhà cung cấp đã tồn tại hay chưa (dùng cho tạo). */
    boolean existsByCode(String code);

    /** Kiểm tra mã nhà cung cấp đã tồn tại ở nhà cung cấp khác (dùng cho cập nhật). */
    boolean existsByCodeAndIdNot(String code, UUID id);

    /**
     * Tìm kiếm phân trang theo mã hoặc tên (không phân biệt hoa thường) và lọc theo
     * trạng thái khi {@code status} được cung cấp (null nghĩa là không lọc trạng thái).
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT s FROM Supplier s
            WHERE (LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR s.status = :status)
            """)
    org.springframework.data.domain.Page<Supplier> search(String q, String status, org.springframework.data.domain.Pageable pageable);
}
