package com.erp.backend_service.repository;

import com.erp.core.domain.Voucher;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Truy vấn dữ liệu voucher. */
@Repository
public interface VoucherRepository extends JpaRepository<Voucher, UUID> {
    Optional<Voucher> findByCodeIgnoreCaseAndStatus(String code, String status);

    /** Kiểm tra mã voucher đã tồn tại hay chưa (dùng cho tạo). */
    boolean existsByCode(String code);

    /**
     * Tìm kiếm phân trang theo mã/tên (không phân biệt hoa thường), lọc theo trạng thái.
     */
    @Query("""
            select v from Voucher v
            where (:status is null or v.status = :status)
              and (:search is null or :search = ''
                or lower(v.code) like concat('%', lower(:search), '%')
                or lower(v.name) like concat('%', lower(:search), '%'))
            """)
    Page<Voucher> search(String search, String status, Pageable pageable);

    /** Khóa bi quan voucher để chống vượt hạn mức khi nhiều yêu cầu áp dụng đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") UUID id);
}