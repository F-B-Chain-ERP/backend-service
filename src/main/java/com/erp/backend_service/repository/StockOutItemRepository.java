package com.erp.backend_service.repository;

import com.erp.core.domain.StockOutItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu dòng chi tiết phiếu xuất kho (stock_out_item). */
@Repository
public interface StockOutItemRepository extends JpaRepository<StockOutItem, UUID> {

    /** Lấy tất cả dòng chi tiết theo phiếu xuất kho. */
    List<StockOutItem> findByStockOutId(UUID stockOutId);

    /** Xóa tất cả dòng chi tiết theo phiếu xuất kho. */
    void deleteByStockOutId(UUID stockOutId);
}