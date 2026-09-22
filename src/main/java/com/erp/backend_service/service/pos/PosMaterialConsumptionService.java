package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.OrderItemRepository;
import com.erp.backend_service.repository.OrderItemToppingRepository;
import com.erp.backend_service.repository.ProductRecipeItemRepository;
import com.erp.backend_service.repository.ToppingRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.service.UnitConversionService;
import com.erp.backend_service.service.impl.StockBalanceMutationService;
import com.erp.core.domain.Material;
import com.erp.core.domain.Order;
import com.erp.core.domain.OrderItem;
import com.erp.core.domain.OrderItemTopping;
import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.Topping;
import com.erp.core.domain.Unit;
import com.erp.core.domain.Warehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tiêu hao NVL realtime theo đơn bán (kho quán). KHÔNG bảng log riêng, giữ đơn giản:
 * - Trừ 1 lần tại CONFIRMED (sau reserve tồn-ly), cùng transaction với đơn.
 * - Chống trừ trùng bằng machine trạng thái (CONFIRMED chỉ vào 1 lần) +
 *   Idempotency-Key lúc tạo đơn, không bằng unique DB.
 * - Hoàn khi hủy sớm bằng cách TÍNH LẠI từ BOM hiện tại (xấp xỉ đúng; chỉ sai
 *   khi BOM đổi giữa lúc xác nhận và lúc hủy).
 * - Không hoàn khi hủy muộn (hàng đã làm, tính hao hụt).
 *
 * Quy tắc nghiêm (fail-fast để lộ dữ liệu bẩn):
 * - Thiếu tồn -&gt; đơn ở lại PENDING/chặn xác nhận, message nêu tên NVL + cần/còn.
 * - Đơn vị không quy được -&gt; chặn (đi sửa BOM/pack rồi bán tiếp).
 * - Variant không có BOM: không trừ gì (vô hình với sổ NVL, màn đối soát gắn cờ
 *   "chưa CT" để đi phủ BOM sau).
 * - Topping thiếu link material/số lượng: bỏ qua dòng đó (master data còn thưa).
 * - Chi nhánh chưa có kho bán hàng: bỏ qua + warn (lỗi cấu hình, không chặn bán).
 */
@Service
public class PosMaterialConsumptionService {

    private static final Logger log = LoggerFactory.getLogger(PosMaterialConsumptionService.class);
    private static final String ACTIVE = "ACTIVE";

    private final OrderItemRepository orderItemRepository;
    private final OrderItemToppingRepository orderItemToppingRepository;
    private final ToppingRepository toppingRepository;
    private final ProductRecipeItemRepository recipeRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceMutationService balanceMutationService;
    private final UnitConversionService unitConversionService;

    public PosMaterialConsumptionService(OrderItemRepository orderItemRepository,
                                         OrderItemToppingRepository orderItemToppingRepository,
                                         ToppingRepository toppingRepository,
                                         ProductRecipeItemRepository recipeRepository,
                                         MaterialRepository materialRepository,
                                         UnitRepository unitRepository,
                                         WarehouseRepository warehouseRepository,
                                         StockBalanceMutationService balanceMutationService,
                                         UnitConversionService unitConversionService) {
        this.orderItemRepository = orderItemRepository;
        this.orderItemToppingRepository = orderItemToppingRepository;
        this.toppingRepository = toppingRepository;
        this.recipeRepository = recipeRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.warehouseRepository = warehouseRepository;
        this.balanceMutationService = balanceMutationService;
        this.unitConversionService = unitConversionService;
    }

    /** Trừ NVL cho đơn vừa CONFIRMED. Thiếu/chưa chuẩn dữ liệu thì ném để đơn không đi tiếp. */
    @Transactional
    public void deductForOrder(Order order) {
        Warehouse warehouse = resolveSellingWarehouseOrNull(order.getBranchId());
        if (warehouse == null) {
            log.warn("Bỏ qua trừ NVL realtime cho đơn {}: chi nhánh {} chưa có kho bán hàng",
                order.getOrderCode(), order.getBranchId());
            return;
        }
        Map<UUID, BigDecimal> needed = aggregateNeed(order);
        if (needed.isEmpty()) {
            return;
        }
        Map<UUID, Material> materials = materialRepository.findAllById(needed.keySet()).stream()
            .collect(java.util.stream.Collectors.toMap(Material::getId, m -> m, (a, b) -> a));
        for (Map.Entry<UUID, BigDecimal> entry : needed.entrySet()) {
            BigDecimal quantity = entry.getValue().setScale(3, RoundingMode.HALF_UP);
            if (quantity.signum() <= 0) {
                continue;
            }
            try {
                balanceMutationService.decreaseForSale(warehouse.getId(), entry.getKey(), quantity);
            } catch (BaseException e) {
                if (!ErrorCode.INV_400_INSUFFICIENT_STOCK.equals(e.getErrorCode())) {
                    throw e;
                }
                Material material = materials.get(entry.getKey());
                throw new BaseException(ErrorCode.INV_400_INSUFFICIENT_STOCK,
                    "NVL " + materialCode(material, entry.getKey()) + " không đủ"
                        + " (cần " + quantity.stripTrailingZeros().toPlainString()
                        + " " + baseUnitCode(material) + " cho đơn " + order.getOrderCode() + ").");
            }
        }
    }

    /** Hoàn khi hủy sớm: tính lại từ BOM hiện tại (xấp xỉ, xem ghi chú class). */
    @Transactional
    public void releaseForOrder(Order order) {
        Warehouse warehouse = resolveSellingWarehouseOrNull(order.getBranchId());
        if (warehouse == null) {
            log.warn("Bỏ qua hoàn NVL cho đơn hủy {}: chi nhánh {} chưa có kho bán hàng",
                order.getOrderCode(), order.getBranchId());
            return;
        }
        Map<UUID, BigDecimal> needed = aggregateNeed(order);
        for (Map.Entry<UUID, BigDecimal> entry : needed.entrySet()) {
            BigDecimal quantity = entry.getValue().setScale(3, RoundingMode.HALF_UP);
            if (quantity.signum() <= 0) {
                continue;
            }
            balanceMutationService.increaseForSale(warehouse.getId(), entry.getKey(), quantity);
        }
    }

    /** Tổng NVL cần cho cả đơn, quy về đơn vị gốc. */
    private Map<UUID, BigDecimal> aggregateNeed(Order order) {
        Map<UUID, BigDecimal> needed = new HashMap<>();
        List<OrderItem> items = orderItemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(
            order.getId(), ACTIVE);
        for (OrderItem item : items) {
            if (item.getVariantId() == null || item.getQuantity() == null) {
                continue;
            }
            List<ProductRecipeItem> recipes =
                recipeRepository.findByVariantIdAndStatusOrderByCreatedAtAsc(
                    item.getVariantId(), ACTIVE);
            if (recipes.isEmpty()) {
                continue;
            }
            Map<UUID, Material> materials = materialRepository.findAllById(recipes.stream()
                    .map(ProductRecipeItem::getMaterialId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Material::getId, m -> m, (a, b) -> a));
            for (ProductRecipeItem recipe : recipes) {
                Material material = materials.get(recipe.getMaterialId());
                if (material == null) {
                    continue;
                }
                BigDecimal bomQuantity = recipe.getQuantity() != null ? recipe.getQuantity() : BigDecimal.ZERO;
                BigDecimal wastage = recipe.getWastagePercent() != null ? recipe.getWastagePercent() : BigDecimal.ZERO;
                BigDecimal perCup = bomQuantity.multiply(BigDecimal.ONE.add(
                    wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
                BigDecimal perCupBase = unitConversionService.convertToBaseUnit(
                    perCup, recipe.getUnitId(), material);
                needed.merge(recipe.getMaterialId(),
                    perCupBase.multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
            }
            aggregateToppings(item, needed);
        }
        return needed;
    }

    /** Topping: số lượng lưu là TỔNG cả line (giả thiết A2), nhân với định mức NVL/topping. */
    private void aggregateToppings(OrderItem item, Map<UUID, BigDecimal> needed) {
        List<OrderItemTopping> toppings =
            orderItemToppingRepository.findByOrderItemIdAndStatus(item.getId(), ACTIVE);
        for (OrderItemTopping orderTopping : toppings) {
            if (orderTopping.getQuantity() == null || orderTopping.getQuantity() <= 0) {
                continue;
            }
            Topping topping = toppingRepository.findById(orderTopping.getToppingId()).orElse(null);
            if (topping == null || topping.getMaterialId() == null
                || topping.getMaterialQuantity() == null) {
                log.debug("Bỏ qua trừ NVL topping {} của đơn: thiếu link material/số lượng",
                    orderTopping.getToppingId());
                continue;
            }
            needed.merge(topping.getMaterialId(),
                topping.getMaterialQuantity().multiply(BigDecimal.valueOf(orderTopping.getQuantity())),
                BigDecimal::add);
        }
    }

    /** Kho bán hàng duy nhất của chi nhánh; null = chưa cấu hình (bỏ qua + warn ở caller). */
    private Warehouse resolveSellingWarehouseOrNull(UUID branchId) {
        List<Warehouse> candidates = warehouseRepository.findByBranchId(branchId).stream()
            .filter(w -> ACTIVE.equals(w.getStatus()) && !"CENTRAL".equals(w.getWarehouseType()))
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Chi nhánh có " + candidates.size()
                    + " kho bán hàng, cần chỉ định 1 kho bán hàng chính trước khi trừ NVL realtime.");
        }
        return candidates.get(0);
    }

    private String materialCode(Material material, UUID materialId) {
        if (material != null && material.getCode() != null) {
            return material.getCode();
        }
        return materialId.toString();
    }

    private String baseUnitCode(Material material) {
        if (material == null || material.getBaseUnitId() == null) {
            return "";
        }
        return unitRepository.findById(material.getBaseUnitId())
            .map(Unit::getCode).orElse("");
    }
}
