package com.erp.backend_service.repository;

import com.erp.core.domain.Voucher;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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

    /** Kiểm tra mã voucher đã tồn tại hay chưa (dùng cho tạo). */
    boolean existsByCode(String code);

    /**
     * Tìm kiếm phân trang theo mã/tên/mô tả (không phân biệt hoa thường), lọc theo
     * trạng thái, loại giảm giá, khoảng thời gian hiệu lực và chi nhánh được gán.
     */
    @Query("""
            select v from Voucher v
            where (:status is null or v.status = :status)
              and (:discountType is null or v.discountType = :discountType)
              and (:search is null or :search = ''
                or lower(v.code) like concat('%', lower(:search), '%')
                or lower(v.name) like concat('%', lower(:search), '%')
                or lower(COALESCE(v.description, '')) like concat('%', lower(:search), '%'))
              and (:startFrom is null or v.startAt >= :startFrom)
              and (:startTo is null or v.startAt <= :startTo)
              and (:endFrom is null or v.endAt >= :endFrom)
              and (:endTo is null or v.endAt <= :endTo)
              and (:branchId is null
                or exists (select 1 from VoucherBranch vb where vb.voucherId = v.id and vb.branchId = :branchId))
            """)
    Page<Voucher> search(String search, String status, String discountType,
                         Instant startFrom, Instant startTo, Instant endFrom, Instant endTo,
                         UUID branchId, Pageable pageable);

    /** Khóa bi quan voucher để chống vượt hạn mức khi nhiều yêu cầu áp dụng đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Voucher v where v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") UUID id);
}