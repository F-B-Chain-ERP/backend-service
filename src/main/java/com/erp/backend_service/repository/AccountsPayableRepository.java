package com.erp.backend_service.repository;

import com.erp.core.domain.AccountsPayable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Truy vấn dữ liệu công nợ phải trả (accounts_payable).
 */
@Repository
public interface AccountsPayableRepository extends JpaRepository<AccountsPayable, UUID> {

    /**
     * Kiểm tra đơn mua hàng đã có công nợ liên kết hay chưa.
     */
    boolean existsByPurchaseOrderId(UUID purchaseOrderId);

    /**
     * Kiểm tra số hóa đơn đã tồn tại trong hệ thống hay chưa.
     */
    boolean existsByInvoiceNo(String invoiceNo);

    /**
     * Kiểm tra số hóa đơn đã tồn tại ở công nợ khác (dùng cho cập nhật).
     */
    boolean existsByInvoiceNoAndIdNot(String invoiceNo, UUID id);

    /**
     * Tìm công nợ theo đơn mua hàng.
     */
    List<AccountsPayable> findByPurchaseOrderId(UUID purchaseOrderId);

    /**
     * Tìm kiếm phân trang theo số hóa đơn, ghi chú, mã PO, trạng thái, NCC và khoảng hạn thanh toán.
     */
    @Query("""
            select ap from AccountsPayable ap
            left join com.erp.core.domain.PurchaseOrder po on ap.purchaseOrderId = po.id
            where (:status is null or ap.status = :status)
              and (:supplierId is null or ap.supplierId = :supplierId)
              and (cast(:dueFrom as localdate) is null or ap.dueDate >= :dueFrom)
              and (cast(:dueTo as localdate) is null or ap.dueDate <= :dueTo)
              and (:search is null or :search = ''
                   or lower(coalesce(ap.invoiceNo, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(ap.note, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(po.poCode, '')) like concat('%', lower(:search), '%'))
            """)
    Page<AccountsPayable> search(@Param("search") String search,
                                 @Param("status") String status,
                                 @Param("supplierId") UUID supplierId,
                                 @Param("dueFrom") LocalDate dueFrom,
                                 @Param("dueTo") LocalDate dueTo,
                                 Pageable pageable);

    /**
     * Tìm kiếm + sort theo số tiền còn nợ tăng dần (computed: invoiceAmount - paidAmount).
     */
    @Query("""
            select ap from AccountsPayable ap
            left join com.erp.core.domain.PurchaseOrder po on ap.purchaseOrderId = po.id
            where (:status is null or ap.status = :status)
              and (:supplierId is null or ap.supplierId = :supplierId)
              and (cast(:dueFrom as localdate) is null or ap.dueDate >= :dueFrom)
              and (cast(:dueTo as localdate) is null or ap.dueDate <= :dueTo)
              and (:search is null or :search = ''
                   or lower(coalesce(ap.invoiceNo, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(ap.note, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(po.poCode, '')) like concat('%', lower(:search), '%'))
            order by (ap.invoiceAmount - ap.paidAmount) asc
            """)
    Page<AccountsPayable> searchOrderByRemainingAsc(@Param("search") String search,
                                                    @Param("status") String status,
                                                    @Param("supplierId") UUID supplierId,
                                                    @Param("dueFrom") LocalDate dueFrom,
                                                    @Param("dueTo") LocalDate dueTo,
                                                    Pageable pageable);

    /**
     * Tìm kiếm + sort theo số tiền còn nợ giảm dần.
     */
    @Query("""
            select ap from AccountsPayable ap
            left join com.erp.core.domain.PurchaseOrder po on ap.purchaseOrderId = po.id
            where (:status is null or ap.status = :status)
              and (:supplierId is null or ap.supplierId = :supplierId)
              and (cast(:dueFrom as localdate) is null or ap.dueDate >= :dueFrom)
              and (cast(:dueTo as localdate) is null or ap.dueDate <= :dueTo)
              and (:search is null or :search = ''
                   or lower(coalesce(ap.invoiceNo, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(ap.note, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(po.poCode, '')) like concat('%', lower(:search), '%'))
            order by (ap.invoiceAmount - ap.paidAmount) desc
            """)
    Page<AccountsPayable> searchOrderByRemainingDesc(@Param("search") String search,
                                                     @Param("status") String status,
                                                     @Param("supplierId") UUID supplierId,
                                                     @Param("dueFrom") LocalDate dueFrom,
                                                     @Param("dueTo") LocalDate dueTo,
                                                     Pageable pageable);

    /**
     * Tìm kiếm + sort hạn thanh toán giảm dần, ép sentinel (chưa đặt hạn) xuống cuối.
     */
    @Query("""
            select ap from AccountsPayable ap
            left join com.erp.core.domain.PurchaseOrder po on ap.purchaseOrderId = po.id
            where (:status is null or ap.status = :status)
              and (:supplierId is null or ap.supplierId = :supplierId)
              and (cast(:dueFrom as localdate) is null or ap.dueDate >= :dueFrom)
              and (cast(:dueTo as localdate) is null or ap.dueDate <= :dueTo)
              and (:search is null or :search = ''
                   or lower(coalesce(ap.invoiceNo, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(ap.note, '')) like concat('%', lower(:search), '%')
                   or lower(coalesce(po.poCode, '')) like concat('%', lower(:search), '%'))
            order by case when ap.dueDate is null or ap.dueDate >= :sentinel then 1 else 0 end,
                     ap.dueDate desc
            """)
    Page<AccountsPayable> searchOrderByDueDateDesc(@Param("search") String search,
                                                   @Param("status") String status,
                                                   @Param("supplierId") UUID supplierId,
                                                   @Param("dueFrom") LocalDate dueFrom,
                                                   @Param("dueTo") LocalDate dueTo,
                                                   @Param("sentinel") LocalDate sentinel,
                                                   Pageable pageable);

    /**
     * Tìm các công nợ quá hạn: due_date đã qua và trạng thái chưa thanh toán/đã trả một phần.
     */
    @Query("""
            select ap from AccountsPayable ap
            where ap.status in ('UNPAID', 'PARTIALLY_PAID')
              and ap.dueDate is not null
              and ap.dueDate < :today
              and ap.dueDate < :sentinel
            """)
    List<AccountsPayable> findOverduePayables(@Param("today") LocalDate today,
                                              @Param("sentinel") LocalDate sentinel);

    /** Tổng nợ còn phải trả (chỉ tính UNPAID + PARTIALLY_PAID). */
    @Query("""
            select coalesce(sum(ap.invoiceAmount - ap.paidAmount), 0)
            from AccountsPayable ap
            where ap.status in ('UNPAID', 'PARTIALLY_PAID')
            """)
    BigDecimal sumRemainingAll();

    /** Tổng nợ quá hạn (chỉ tính OVERDUE). */
    @Query("""
            select coalesce(sum(ap.invoiceAmount - ap.paidAmount), 0)
            from AccountsPayable ap
            where ap.status = 'OVERDUE'
            """)
    BigDecimal sumOverdueAll();

    /** Tất cả purchaseOrderId đã có trong accounts_payable (để filter PO). */
    @Query("select distinct ap.purchaseOrderId from AccountsPayable ap where ap.purchaseOrderId is not null")
    Set<UUID> findExistingPurchaseOrderIds();

    /** Tim invoiceNo cuoi cung bat dau bang prefix (de CodeGenerator tiep sequence). */
    @Query("select ap.invoiceNo from AccountsPayable ap where ap.invoiceNo like concat(:prefix, '%') order by ap.invoiceNo desc")
    Optional<String> findTopInvoiceNoByPrefix(@Param("prefix") String prefix);
}
