package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.ProductRecipeItemRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.service.UnitConversionService;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.ProductVariant;
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
import java.util.stream.Collectors;

/**
 * Cửa ải bán hàng theo năng lực NVL (Mức 2: bỏ chốt tồn ly mỗi ngày).
 * - Số ly tối đa pha được = min theo BOM trên tồn khả dụng (onHand - reserved)
 *   tại kho bán hàng của chi nhánh, quy về đơn vị gốc.
 * - null = không xác định được (chưa có BOM / chưa cấu hình kho / thiếu dữ liệu):
 *   cho qua ở bước kiểm tra sớm, chốt chặn cuối ở
 *   {@link PosMaterialConsumptionService#deductForOrder} vẫn báo rõ khi trừ thật.
 */
@Service
public class PosCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(PosCapabilityService.class);
    private static final String ACTIVE = "ACTIVE";

    private final WarehouseRepository warehouseRepository;
    private final MaterialStockBalanceRepository balanceRepository;
    private final ProductRecipeItemRepository recipeRepository;
    private final MaterialRepository materialRepository;
    private final ProductVariantRepository variantRepository;
    private final UnitConversionService unitConversionService;

    public PosCapabilityService(WarehouseRepository warehouseRepository,
                                MaterialStockBalanceRepository balanceRepository,
                                ProductRecipeItemRepository recipeRepository,
                                MaterialRepository materialRepository,
                                ProductVariantRepository variantRepository,
                                UnitConversionService unitConversionService) {
        this.warehouseRepository = warehouseRepository;
        this.balanceRepository = balanceRepository;
        this.recipeRepository = recipeRepository;
        this.materialRepository = materialRepository;
        this.variantRepository = variantRepository;
        this.unitConversionService = unitConversionService;
    }

    /**
     * Số ly tối đa pha được ngay lúc này cho 1 biến thể tại chi nhánh.
     * null = không xác định (cho qua ở kiểm tra sớm).
     */
    @Transactional(readOnly = true)
    public Integer capabilityForVariant(UUID branchId, UUID variantId) {
        if (branchId == null || variantId == null) {
            return null;
        }
        Warehouse warehouse = resolveSellingWarehouseOrNull(branchId);
        if (warehouse == null) {
            log.warn("Chưa có kho bán hàng cho chi nhánh {}, bỏ qua kiểm tra năng lực NVL", branchId);
            return null;
        }
        List<ProductRecipeItem> recipes = recipeRepository.findByVariantIdInAndStatus(List.of(variantId), ACTIVE);
        if (recipes.isEmpty()) {
            return null;
        }
        Map<UUID, Material> materials = materialRepository.findAllById(
                recipes.stream().map(ProductRecipeItem::getMaterialId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Material::getId, m -> m, (a, b) -> a));
        Map<UUID, BigDecimal> availableByMaterial = new HashMap<>();
        for (MaterialStockBalance b : balanceRepository.findByWarehouseId(warehouse.getId())) {
            BigDecimal available = nvl(b.getQuantityOnHand()).subtract(nvl(b.getQuantityReserved()));
            availableByMaterial.merge(b.getMaterialId(), available, BigDecimal::add);
        }
        BigDecimal best = null;
        for (ProductRecipeItem recipe : recipes) {
            Material material = materials.get(recipe.getMaterialId());
            BigDecimal available = availableByMaterial.get(recipe.getMaterialId());
            if (material == null || available == null) {
                return null;
            }
            BigDecimal perCupBase = perCupBase(recipe, material);
            if (perCupBase == null || perCupBase.signum() <= 0) {
                return null;
            }
            BigDecimal cups = available.divide(perCupBase, 0, RoundingMode.FLOOR);
            if (best == null || cups.compareTo(best) < 0) {
                best = cups;
            }
        }
        if (best == null) {
            return null;
        }
        try {
            return Math.max(0, best.intValueExact());
        } catch (ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Chặn bán sớm khi NVL chỉ đủ pha ít hơn số lượng yêu cầu.
     * Không xác định được năng lực thì cho qua (chốt chặn cuối ở lúc trừ NVL thật).
     */
    @Transactional(readOnly = true)
    public void checkSaleable(UUID branchId, UUID variantId, int quantity) {
        if (variantId == null || quantity <= 0) {
            return;
        }
        Integer capability = capabilityForVariant(branchId, variantId);
        if (capability == null || capability >= quantity) {
            return;
        }
        throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY,
                "Sản phẩm " + variantLabel(variantId) + " chỉ còn đủ NVL pha được "
                        + capability + " ly (cần " + quantity + ").");
    }

    /** Định mức NVL/ly quy về đơn vị gốc (kèm hao hụt); null = không quy được.
     * Dùng bản lenient (catch trong proxy): bản strict ném qua @Transactional sẽ đánh
     * dấu rollback-only cả transaction ngoài (đang tạo đơn), che mất lỗi gốc. */
    private BigDecimal perCupBase(ProductRecipeItem recipe, Material material) {
        BigDecimal bomQuantity = recipe.getQuantity() != null ? recipe.getQuantity() : BigDecimal.ZERO;
        BigDecimal wastage = recipe.getWastagePercent() != null ? recipe.getWastagePercent() : BigDecimal.ZERO;
        BigDecimal perCup = bomQuantity.multiply(BigDecimal.ONE.add(
                wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
        if (perCup.signum() <= 0) {
            return null;
        }
        BigDecimal converted = unitConversionService.convertToBaseUnitLenient(perCup, recipe.getUnitId(), material);
        if (converted == null) {
            log.warn("Không quy được đơn vị BOM của variant {}", recipe.getVariantId());
        }
        return converted;
    }

    /** Kho bán hàng duy nhất của chi nhánh; null = chưa cấu hình (cho qua + warn ở caller). */
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
                            + " kho bán hàng, cần chỉ định 1 kho bán hàng chính trước khi bán theo năng lực NVL.");
        }
        return candidates.get(0);
    }

    private String variantLabel(UUID variantId) {
        return variantRepository.findById(variantId)
                .map(v -> v.getVariantCode() + " - " + v.getVariantName())
                .orElse(variantId.toString());
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
