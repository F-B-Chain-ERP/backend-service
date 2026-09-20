package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.AccountsPayableMapper;
import com.erp.backend_service.mapper.PayablePaymentMapper;
import com.erp.backend_service.repository.AccountsPayablePaymentRepository;
import com.erp.backend_service.repository.AccountsPayableRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.util.CodeGenerator;
import com.erp.backend_service.service.AccountsPayableService;
import com.erp.core.domain.AccountsPayable;
import com.erp.core.domain.AccountsPayablePayment;
import com.erp.core.domain.PurchaseOrder;
import com.erp.core.domain.Supplier;
import com.erp.core.dto.request.fin.CreateAccountsPayableRequest;
import com.erp.core.dto.request.fin.CreatePayablePaymentRequest;
import com.erp.core.dto.request.fin.UpdateAccountsPayableRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.AccountsPayableDetailResponse;
import com.erp.core.dto.response.fin.AccountsPayableSummaryResponse;
import com.erp.core.dto.response.fin.PayablePaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Triển khai {@link AccountsPayableService}: quản lý công nợ phải trả
 * với các nghiệp vụ CRUD, ghi nhận thanh toán và kiểm tra quá hạn.
 */
@Service
public class AccountsPayableServiceImpl implements AccountsPayableService {

    private static final Logger log = LoggerFactory.getLogger(AccountsPayableServiceImpl.class);

    private static final int FIXED_PAGE_SIZE = 10;

    /** Trạng thái cho phép ghi nhận thanh toán. */
    private static final Set<String> PAYABLE_STATUSES_ALLOW_PAYMENT =
            Set.of("UNPAID", "PARTIALLY_PAID", "OVERDUE");

    /** Trạng thái khi nhận hàng chưa có hóa đơn. */
    private static final String STATUS_UNPAID = "UNPAID";
    private static final String STATUS_PARTIALLY_PAID = "PARTIALLY_PAID";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_OVERDUE = "OVERDUE";

    /** Ngày sentinel đại diện cho "chưa có hạn thanh toán" (DB NOT NULL). */
    private static final LocalDate SENTINEL_NO_DUE_DATE = LocalDate.of(9999, 12, 31);

    /** Trạng thái PO cho phép tạo công nợ. */
    private static final Set<String> PO_RECEIVABLE_STATUSES =
            Set.of("APPROVED", "PARTIALLY_RECEIVED", "RECEIVED");

    private final AccountsPayableRepository accountsPayableRepository;
    private final AccountsPayablePaymentRepository accountsPayablePaymentRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AccountsPayableMapper accountsPayableMapper;
    private final PayablePaymentMapper payablePaymentMapper;

    public AccountsPayableServiceImpl(AccountsPayableRepository accountsPayableRepository,
                                      AccountsPayablePaymentRepository accountsPayablePaymentRepository,
                                      SupplierRepository supplierRepository,
                                      PurchaseOrderRepository purchaseOrderRepository,
                                      AccountsPayableMapper accountsPayableMapper,
                                      PayablePaymentMapper payablePaymentMapper) {
        this.accountsPayableRepository = accountsPayableRepository;
        this.accountsPayablePaymentRepository = accountsPayablePaymentRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.accountsPayableMapper = accountsPayableMapper;
        this.payablePaymentMapper = payablePaymentMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountsPayableSummaryResponse> list(int page, int size, String search,
                                                             String status, UUID supplierId,
                                                             LocalDate dueFrom, LocalDate dueTo,
                                                             String sortBy, String sortDir) {
        String cleanSearch = StringUtils.hasText(search) ? search.trim() : null;
        int pageIdx = Math.max(page, 0);
        int pageSize = Math.min(size, 10);
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        Page<AccountsPayable> apPage;

        if ("remaining".equals(sortBy)) {
            // Còn nợ là computed (invoiceAmount - paidAmount) → query sort riêng
            Pageable pageable = PageRequest.of(pageIdx, pageSize);
            apPage = asc
                    ? accountsPayableRepository.searchOrderByRemainingAsc(
                            cleanSearch, status, supplierId, dueFrom, dueTo, pageable)
                    : accountsPayableRepository.searchOrderByRemainingDesc(
                            cleanSearch, status, supplierId, dueFrom, dueTo, pageable);
        } else if ("dueDate".equals(sortBy) && !asc) {
            // dueDate DESC: ép sentinel (chưa đặt hạn) xuống cuối qua query riêng
            Pageable pageable = PageRequest.of(pageIdx, pageSize);
            apPage = accountsPayableRepository.searchOrderByDueDateDesc(
                    cleanSearch, status, supplierId, dueFrom, dueTo, SENTINEL_NO_DUE_DATE, pageable);
        } else {
            // createdAt mọi chiều + dueDate ASC (sentinel tự xuống cuối) → sort qua Pageable
            String property = "dueDate".equals(sortBy) ? "dueDate" : "createdAt";
            Sort sort = asc ? Sort.by(property).ascending() : Sort.by(property).descending();
            Pageable pageable = PageRequest.of(pageIdx, pageSize, sort);
            apPage = accountsPayableRepository.search(
                    cleanSearch, status, supplierId, dueFrom, dueTo, pageable);
        }

        List<AccountsPayableSummaryResponse> content = apPage.getContent().stream()
                .map(ap -> toSummaryResponse(ap))
                .toList();

        return new PageResponse<>(
                apPage.getNumber(),
                apPage.getSize(),
                apPage.getTotalElements(),
                apPage.getTotalPages(),
                content
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AccountsPayableDetailResponse get(UUID id) {
        AccountsPayable ap = findById(id);
        List<PayablePaymentResponse> payments = getPayments(id);
        return toDetailResponse(ap, payments);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountsPayableSummaryResponse create(CreateAccountsPayableRequest request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_SUPPLIER_NOT_FOUND));

        if (!"ACTIVE".equals(supplier.getStatus())) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_SUPPLIER_INACTIVE);
        }

        // Validate PO liên kết (nếu có)
        PurchaseOrder po = null;
        if (request.purchaseOrderId() != null) {
            po = purchaseOrderRepository.findById(request.purchaseOrderId())
                    .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_PO_NOT_FOUND));

            if (!PO_RECEIVABLE_STATUSES.contains(po.getStatus())) {
                throw new BaseException(ErrorCode.FIN_400_PAYABLE_PO_NOT_RECEIVABLE);
            }

            if (accountsPayableRepository.existsByPurchaseOrderId(request.purchaseOrderId())) {
                throw new BaseException(ErrorCode.FIN_400_PAYABLE_EXISTS_FOR_PO);
            }
        }

        // Validate duplicate invoice_no (neu nguoi dung nhap)
        if (StringUtils.hasText(request.invoiceNo())
                && accountsPayableRepository.existsByInvoiceNo(request.invoiceNo())) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_INVOICE_NO_DUPLICATE);
        }

        // dueDate = today + supplier.paymentTermDays (neu khong truyen dueDate)
        int termDays = supplier.getPaymentTermDays() != null ? supplier.getPaymentTermDays() : 0;
        LocalDate dueDate = request.dueDate() != null
                ? request.dueDate()
                : (termDays > 0 ? LocalDate.now().plusDays(termDays) : SENTINEL_NO_DUE_DATE);

        // InvoiceNo: use user input or auto-generate
        String invoiceNo = StringUtils.hasText(request.invoiceNo())
                ? request.invoiceNo().trim()
                : generateInvoiceNo();

        AccountsPayable ap = new AccountsPayable();
        ap.setSupplierId(supplier.getId());
        ap.setPurchaseOrderId(request.purchaseOrderId());
        ap.setInvoiceNo(invoiceNo);
        ap.setInvoiceAmount(request.invoiceAmount());
        ap.setPaidAmount(BigDecimal.ZERO);
        ap.setDueDate(dueDate);
        ap.setStatus(resolveStatus(dueDate, BigDecimal.ZERO));
        ap.setNote(request.note());

        ap = accountsPayableRepository.save(ap);
        return toSummaryResponse(ap);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountsPayableSummaryResponse update(UUID id, UpdateAccountsPayableRequest request) {
        AccountsPayable ap = findById(id);

        // Chỉ cho phép sửa khi UNPAID, chưa có due_date, chưa có payment
        if (!STATUS_UNPAID.equals(ap.getStatus())) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_CANNOT_EDIT);
        }
        if (ap.getDueDate() != null && !SENTINEL_NO_DUE_DATE.equals(ap.getDueDate())) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_CANNOT_EDIT);
        }
        if (accountsPayablePaymentRepository.countByAccountsPayableId(id) > 0) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_CANNOT_EDIT);
        }

        // Validate duplicate invoice_no (nếu thay đổi)
        if (StringUtils.hasText(request.invoiceNo())
                && !request.invoiceNo().equals(ap.getInvoiceNo())
                && accountsPayableRepository.existsByInvoiceNoAndIdNot(request.invoiceNo(), id)) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_INVOICE_NO_DUPLICATE);
        }

        ap.setInvoiceNo(request.invoiceNo());
        ap.setInvoiceAmount(request.invoiceAmount());
        ap.setDueDate(request.dueDate() != null ? request.dueDate() : SENTINEL_NO_DUE_DATE);
        ap.setStatus(resolveStatus(ap.getDueDate(), ap.getPaidAmount()));
        ap.setNote(request.note());

        ap = accountsPayableRepository.save(ap);
        return toSummaryResponse(ap);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        AccountsPayable ap = findById(id);

        if (!STATUS_UNPAID.equals(ap.getStatus())) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_CANNOT_EDIT);
        }
        if (accountsPayablePaymentRepository.countByAccountsPayableId(id) > 0) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_HAS_PAYMENTS);
        }

        accountsPayableRepository.deleteById(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PayablePaymentResponse recordPayment(UUID payableId, CreatePayablePaymentRequest request) {
        AccountsPayable ap = findById(payableId);

        if (STATUS_PAID.equals(ap.getStatus())) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_INVALID_STATUS);
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_INVALID_AMOUNT);
        }

        BigDecimal remaining = computeRemainingAmount(ap);
        if (request.amount().compareTo(remaining) > 0) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_AMOUNT_EXCEED);
        }

        // Tạo bản ghi thanh toán
        AccountsPayablePayment payment = new AccountsPayablePayment();
        payment.setAccountsPayableId(payableId);
        payment.setPaymentDate(request.paymentDate());
        payment.setAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setReferenceNo(request.referenceNo());
        payment.setStatus("ACTIVE");

        accountsPayablePaymentRepository.save(payment);

        // Cập nhật paid_amount và status
        ap.setPaidAmount(ap.getPaidAmount().add(request.amount()));
        ap.setStatus(computeStatus(ap));
        accountsPayableRepository.save(ap);

        return payablePaymentMapper.toResponse(payment);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<PayablePaymentResponse> getPayments(UUID payableId) {
        findById(payableId); // Validate tồn tại
        return accountsPayablePaymentRepository
                .findByAccountsPayableIdOrderByPaymentDateAsc(payableId)
                .stream()
                .map(payablePaymentMapper::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void checkAndMarkOverdue() {
        LocalDate today = LocalDate.now();
        List<AccountsPayable> overduePayables =
                accountsPayableRepository.findOverduePayables(today, SENTINEL_NO_DUE_DATE);

        for (AccountsPayable ap : overduePayables) {
            ap.setStatus(STATUS_OVERDUE);
            accountsPayableRepository.save(ap);
            log.info("[checkAndMarkOverdue] Đánh dấu OVERDUE cho công nợ {} (dueDate={}, NCC={})",
                    ap.getId(), ap.getDueDate(), ap.getSupplierId());
        }

        if (!overduePayables.isEmpty()) {
            log.info("[checkAndMarkOverdue] Đã đánh dấu {} công nợ quá hạn.", overduePayables.size());
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AccountsPayableSummaryResponse createFromPo(UUID purchaseOrderId) {
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_PO_NOT_FOUND));

        if (!PO_RECEIVABLE_STATUSES.contains(po.getStatus())) {
            throw new BaseException(ErrorCode.FIN_400_PAYABLE_PO_NOT_RECEIVABLE);
        }

        // Kiểm tra chưa có payable nào cho PO này
        if (accountsPayableRepository.existsByPurchaseOrderId(purchaseOrderId)) {
            log.info("[createFromPo] PO {} đã có công nợ liên kết, bỏ qua.", po.getPoCode());
            return null;
        }

        Supplier supplier = supplierRepository.findById(po.getSupplierId())
                .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_SUPPLIER_NOT_FOUND));

        // dueDate = today + supplier.paymentTermDays
        int termDays = supplier.getPaymentTermDays() != null ? supplier.getPaymentTermDays() : 0;
        LocalDate dueDate = termDays > 0 ? LocalDate.now().plusDays(termDays) : SENTINEL_NO_DUE_DATE;

        AccountsPayable ap = new AccountsPayable();
        ap.setSupplierId(supplier.getId());
        ap.setPurchaseOrderId(po.getId());
        ap.setInvoiceNo(generateInvoiceNo());
        ap.setInvoiceAmount(po.getTotalAmount());
        ap.setPaidAmount(BigDecimal.ZERO);
        ap.setDueDate(dueDate);
        ap.setStatus(resolveStatus(dueDate, BigDecimal.ZERO));
        ap.setNote("Tự động tạo từ đơn mua hàng " + po.getPoCode());

        ap = accountsPayableRepository.save(ap);
        log.info("[createFromPo] Đã tạo công nợ từ PO {} (số tiền {})",
                po.getPoCode(), po.getTotalAmount());

        return toSummaryResponse(ap);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getSummary() {
        BigDecimal totalRemaining = accountsPayableRepository.sumRemainingAll();
        BigDecimal totalOverdue = accountsPayableRepository.sumOverdueAll();
        return Map.of(
                "totalRemaining", totalRemaining != null ? totalRemaining : BigDecimal.ZERO,
                "totalOverdue", totalOverdue != null ? totalOverdue : BigDecimal.ZERO
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getExistingPoIds() {
        return accountsPayableRepository.findExistingPurchaseOrderIds();
    }

    // ── Private helpers ───────────────────────────────────────────────

    private String resolveStatus(LocalDate dueDate, BigDecimal paidAmount) {
        boolean isOverdue = dueDate != null
                && !SENTINEL_NO_DUE_DATE.equals(dueDate)
                && dueDate.isBefore(LocalDate.now());
        if (isOverdue) {
            return STATUS_OVERDUE;
        }
        boolean isPaid = paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0;
        return isPaid ? STATUS_PARTIALLY_PAID : STATUS_UNPAID;
    }

    private String generateInvoiceNo() {
        return CodeGenerator.nextDailySequence(
                "HDA-",
                prefix -> accountsPayableRepository
                        .findFirstByInvoiceNoStartingWithOrderByInvoiceNoDesc(prefix)
                        .map(AccountsPayable::getInvoiceNo),
                accountsPayableRepository::existsByInvoiceNo);
    }

    private AccountsPayable findById(UUID id) {
        return accountsPayableRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_PAYABLE_NOT_FOUND));
    }

    private BigDecimal computeRemainingAmount(AccountsPayable ap) {
        BigDecimal invoice = ap.getInvoiceAmount() != null ? ap.getInvoiceAmount() : BigDecimal.ZERO;
        BigDecimal paid = ap.getPaidAmount() != null ? ap.getPaidAmount() : BigDecimal.ZERO;
        return invoice.subtract(paid);
    }

    /**
     * Tính trạng thái dựa trên paid_amount và invoice_amount.
     */
    private String computeStatus(AccountsPayable ap) {
        BigDecimal remaining = computeRemainingAmount(ap);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return STATUS_PAID;
        }
        return STATUS_PARTIALLY_PAID;
    }

    private AccountsPayableSummaryResponse toSummaryResponse(AccountsPayable ap) {
        Supplier supplier = ap.getSupplierId() != null
                ? supplierRepository.findById(ap.getSupplierId()).orElse(null)
                : null;
        PurchaseOrder po = ap.getPurchaseOrderId() != null
                ? purchaseOrderRepository.findById(ap.getPurchaseOrderId()).orElse(null)
                : null;
        Integer paymentTermDays = supplier != null ? supplier.getPaymentTermDays() : null;
        LocalDate receivedDate = ap.getCreatedAt() != null
                ? ap.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                : null;
        return accountsPayableMapper.toSummaryResponse(ap, supplier, po, paymentTermDays, receivedDate);
    }

    private AccountsPayableDetailResponse toDetailResponse(AccountsPayable ap,
                                                           List<PayablePaymentResponse> payments) {
        Supplier supplier = ap.getSupplierId() != null
                ? supplierRepository.findById(ap.getSupplierId()).orElse(null)
                : null;
        PurchaseOrder po = ap.getPurchaseOrderId() != null
                ? purchaseOrderRepository.findById(ap.getPurchaseOrderId()).orElse(null)
                : null;
        Integer paymentTermDays = supplier != null ? supplier.getPaymentTermDays() : null;
        LocalDate receivedDate = ap.getCreatedAt() != null
                ? ap.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                : null;
        return accountsPayableMapper.toDetailResponse(ap, supplier, po, paymentTermDays, receivedDate, payments);
    }
}
