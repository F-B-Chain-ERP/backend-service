package com.erp.backend_service.repository;

import com.erp.core.domain.Branch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu chi nhánh (branch). */
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    /** Kiểm tra mã chi nhánh đã tồn tại hay chưa (dùng cho tạo/cập nhật). */
    boolean existsByCode(String code);

    /** Chi nhánh đang hoạt động cho kênh bán hàng (public, không cần quyền). */
    List<Branch> findByStatusOrderByCodeAsc(String status);

    /** Khóa một chi nhánh trong transaction để tuần tự hóa thao tác mở két. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id")
    Optional<Branch> findByIdForUpdate(@Param("id") UUID id);
}
