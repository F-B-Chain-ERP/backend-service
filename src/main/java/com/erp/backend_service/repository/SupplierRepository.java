package com.erp.backend_service.repository;

import com.erp.core.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /** Tìm kiếm phân trang theo mã, tên, điện thoại, người liên hệ, email, địa chỉ, mã số thuế (không phân biệt hoa thường). */
    @Query("""
            select s from Supplier s
            where (:status is null or s.status = :status) 
                   and (:search is null or :search = ''
                or lower(s.code) like concat('%', lower(:search), '%')
                or lower(s.name) like concat('%', lower(:search), '%')
                or lower(COALESCE(s.phone, '')) like concat('%', lower(:search), '%')
                or lower(COALESCE(s.contactName, '')) like concat('%', lower(:search), '%')
                or lower(COALESCE(s.email, '')) like concat('%', lower(:search), '%')
                or lower(COALESCE(s.address, '')) like concat('%', lower(:search), '%')
                or lower(COALESCE(s.taxCode, '')) like concat('%', lower(:search), '%'))                                                           
            """)
    Page<Supplier> search(String search, String status, Pageable pageable);
}
