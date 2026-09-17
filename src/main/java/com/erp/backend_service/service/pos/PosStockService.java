package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchVariantDailyStockRepository;
import com.erp.backend_service.repository.BranchVariantStockLogRepository;
import com.erp.core.domain.BranchVariantDailyStock;
import com.erp.core.domain.BranchVariantStockLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gom mọi check/trừ/hoàn tồn bán trong ngày về một chỗ (A2, A3).
 * - ensure: lazy seed dòng tồn hôm nay (carryover số dư hôm qua, ngày đầu = 0).
 *   Không cần cron/job, lần check đầu tiên trong ngày tự tạo dòng.
 * - check/reserve/release: giữ PESSIMISTIC_WRITE + ghi stock_log để trace.
 */
@Service
public class PosStockService {

    private final BranchVariantDailyStockRepository stockRepository;
    private final BranchVariantStockLogRepository logRepository;
    private final PosBusinessDay businessDay;

    public PosStockService(BranchVariantDailyStockRepository stockRepository,
                           BranchVariantStockLogRepository logRepository,
                           PosBusinessDay businessDay) {
        this.stockRepository = stockRepository;
        this.logRepository = logRepository;
        this.businessDay = businessDay;
    }

    /**
     * Đảm bảo có dòng tồn hôm nay. Chưa có -> tạo mới, opening = số dư mới nhất
     * (carryover hôm qua), ngày đầu tiên = 0 và quản lý phải restock.
     */
    @Transactional
    public BranchVariantDailyStock ensureToday(UUID branchId, UUID variantId) {
        LocalDate today = businessDay.today(branchId);
        return stockRepository
            .findByBranchIdAndVariantIdAndBusinessDateAndStatus(branchId, variantId, today, "ACTIVE")
            .orElseGet(() -> {
                int opening = stockRepository
                    .findFirstByBranchIdAndVariantIdAndStatusOrderByBusinessDateDesc(branchId, variantId,
                        "ACTIVE")
                    .map(BranchVariantDailyStock::getRemainingQuantity)
                    .orElse(0);
                BranchVariantDailyStock created = new BranchVariantDailyStock();
                created.setBranchId(branchId);
                created.setVariantId(variantId);
                created.setBusinessDate(today);
                created.setOpeningQuantity(Math.max(0, opening));
                created.setRemainingQuantity(Math.max(0, opening));
                created.setSoldQuantity(0);
                created.setStatus("ACTIVE");
                BranchVariantDailyStock saved = stockRepository.save(created);
                writeLog(branchId, variantId, 0, null, "RESTOCK",
                    "Auto carryover tồn sang ngày " + today);
                return saved;
            });
    }

    /**
     * Quản lý chốt tồn mở bán: opening tuyệt đối, remaining = opening - đã bán (kẹp >= 0).
     * Gọi mỗi sáng hoặc khi nhập thêm hàng trong ngày.
     */
    @Transactional
    public BranchVariantDailyStock restock(UUID branchId, UUID variantId, int openingQuantity, String note) {
        if (openingQuantity < 0) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Tồn mở bán không được âm.");
        }
        BranchVariantDailyStock stock = ensureToday(branchId, variantId);
        BranchVariantDailyStock locked = stockRepository
            .lockByBranchVariantDate(branchId, variantId, stock.getBusinessDate(), "ACTIVE")
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY));
        int delta = openingQuantity - locked.getOpeningQuantity();
        locked.setOpeningQuantity(openingQuantity);
        locked.setRemainingQuantity(Math.max(0, openingQuantity - locked.getSoldQuantity()));
        stockRepository.save(locked);
        writeLog(branchId, variantId, delta, null, "RESTOCK", note != null ? note : "Quản lý chốt tồn mở bán");
        return locked;
    }

    @Transactional
    public void checkAvailable(UUID branchId, UUID variantId, int quantity) {
        if (variantId == null) {
            return;
        }
        BranchVariantDailyStock stock = ensureToday(branchId, variantId);
        if (quantity > stock.getRemainingQuantity()) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY);
        }
    }

    @Transactional
    public void reserve(UUID branchId, UUID variantId, int quantity, UUID orderId) {
        if (variantId == null || quantity <= 0) {
            return;
        }
        ensureToday(branchId, variantId);
        LocalDate today = businessDay.today(branchId);
        BranchVariantDailyStock stock = stockRepository
            .lockByBranchVariantDate(branchId, variantId, today, "ACTIVE")
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY,
                "Sản phẩm chưa có tồn trong ngày, vui lòng restock."));
        if (stock.getRemainingQuantity() < quantity) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY);
        }
        stock.setRemainingQuantity(stock.getRemainingQuantity() - quantity);
        stock.setSoldQuantity(stock.getSoldQuantity() + quantity);
        stockRepository.save(stock);
        writeLog(branchId, variantId, -quantity, orderId, "SALE", "Reserve khi CONFIRMED đơn " + orderId);
    }

    @Transactional
    public void release(UUID branchId, UUID variantId, int quantity, UUID orderId) {
        if (variantId == null || quantity <= 0) {
            return;
        }
        // Hoàn vào dòng hôm nay (nơi đơn mới trừ vào), kể cả đơn hôm qua bị hủy muộn.
        ensureToday(branchId, variantId);
        LocalDate today = businessDay.today(branchId);
        BranchVariantDailyStock stock = stockRepository
            .lockByBranchVariantDate(branchId, variantId, today, "ACTIVE")
            .orElse(null);
        if (stock == null) {
            return;
        }
        stock.setRemainingQuantity(stock.getRemainingQuantity() + quantity);
        stock.setSoldQuantity(Math.max(0, stock.getSoldQuantity() - quantity));
        stockRepository.save(stock);
        writeLog(branchId, variantId, quantity, orderId, "ADJUSTMENT", "Hoàn tồn khi hủy/từ chối đơn " + orderId);
    }

    private void writeLog(UUID branchId, UUID variantId, int qtyChange, UUID referenceId, String changeType,
                          String note) {
        BranchVariantStockLog log = new BranchVariantStockLog();
        log.setBranchId(branchId);
        log.setVariantId(variantId);
        log.setChangeType(changeType);
        log.setQuantityChange(qtyChange);
        log.setReferenceId(referenceId);
        log.setNote(note);
        log.setStatus("ACTIVE");
        logRepository.save(log);
    }
}
