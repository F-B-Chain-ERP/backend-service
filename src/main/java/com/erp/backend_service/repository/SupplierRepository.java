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

    boolean existsByTaxCode(String taxCode);

    boolean existsByTaxCodeAndIdNot(String taxCode, UUID id);

    /** Tìm kiếm phân trang theo mã hoặc tên (không phân biệt hoa thường). */
    org.springframework.data.domain.Page<Supplier> findAllByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
            String name, String code, org.springframework.data.domain.Pageable pageable);
}
