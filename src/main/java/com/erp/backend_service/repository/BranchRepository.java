package com.erp.backend_service.repository;

import com.erp.core.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu chi nhánh (branch). */
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    /** Kiểm tra mã chi nhánh đã tồn tại hay chưa (dùng cho tạo/cập nhật). */
    boolean existsByCode(String code);

    /** Chi nhánh đang hoạt động cho kênh bán hàng (public, không cần quyền). */
    List<Branch> findByStatusOrderByCodeAsc(String status);
}
