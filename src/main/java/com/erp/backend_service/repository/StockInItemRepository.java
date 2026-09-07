package com.erp.backend_service.repository;

import com.erp.core.domain.StockInItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu dòng chi tiết phiếu nhập kho (stock_in_item). */
@Repository
public interface StockInItemRepository extends JpaRepository<StockInItem, UUID> {

    /** Lấy tất cả dòng chi tiết theo phiếu nhập kho. */
    List<StockInItem> findByStockInId(UUID stockInId);

    /** Xóa tất cả dòng chi tiết theo phiếu nhập kho. */
    void deleteByStockInId(UUID stockInId);
}