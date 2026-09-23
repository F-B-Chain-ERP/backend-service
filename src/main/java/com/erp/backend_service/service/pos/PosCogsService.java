package com.erp.backend_service.service.pos;

import com.erp.backend_service.repository.ProductRecipeItemRepository;
import com.erp.backend_service.repository.SupplierMaterialRepository;
import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.SupplierMaterial;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * Giả thiết B3: giá vốn 1 đơn vị = Σ BOM.qty × (1+wastage) × giá NCC ưu tiên.
 * Không BOM/giá -> ZERO (không chặn bán) để lòi dữ liệu thiếu mà không sập đơn.
 */
@Service
public class PosCogsService {

    private final ProductRecipeItemRepository recipeRepository;
    private final SupplierMaterialRepository supplierMaterialRepository;

    public PosCogsService(ProductRecipeItemRepository recipeRepository,
                          SupplierMaterialRepository supplierMaterialRepository) {
        this.recipeRepository = recipeRepository;
        this.supplierMaterialRepository = supplierMaterialRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal unitCogs(UUID variantId) {
        if (variantId == null) {
            return BigDecimal.ZERO;
        }
        List<ProductRecipeItem> lines =
            recipeRepository.findByVariantIdAndStatusOrderByCreatedAtAsc(variantId, "ACTIVE");
        if (lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ProductRecipeItem line : lines) {
            BigDecimal price = materialPrice(line.getMaterialId());
            BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            BigDecimal wastage = line.getWastagePercent() != null ? line.getWastagePercent() : BigDecimal.ZERO;
            BigDecimal factor = BigDecimal.ONE.add(wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            total = total.add(price.multiply(qty).multiply(factor));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Tính COGS cho mọi variant của đơn với đúng hai bulk query. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> unitCogsByVariantIds(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        List<ProductRecipeItem> lines = recipeRepository.findByVariantIdInAndStatus(variantIds, "ACTIVE");
        Set<UUID> materialIds = lines.stream().map(ProductRecipeItem::getMaterialId)
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, BigDecimal> priceByMaterial = new HashMap<>();
        if (!materialIds.isEmpty()) {
            supplierMaterialRepository.findByMaterialIdInAndStatus(materialIds, "ACTIVE").stream()
                .collect(Collectors.groupingBy(SupplierMaterial::getMaterialId))
                .forEach((materialId, prices) -> priceByMaterial.put(materialId, prices.stream()
                    .sorted((a, b) -> Boolean.compare(b.isPreferred(), a.isPreferred()))
                    .map(SupplierMaterial::getPurchasePrice).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(BigDecimal.ZERO)));
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (ProductRecipeItem line : lines) {
            BigDecimal price = priceByMaterial.getOrDefault(line.getMaterialId(), BigDecimal.ZERO);
            BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            BigDecimal wastage = line.getWastagePercent() != null ? line.getWastagePercent() : BigDecimal.ZERO;
            BigDecimal factor = BigDecimal.ONE.add(
                wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            result.merge(line.getVariantId(), price.multiply(qty).multiply(factor), BigDecimal::add);
        }
        variantIds.forEach(id -> result.put(id,
            result.getOrDefault(id, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)));
        return result;
    }

    private BigDecimal materialPrice(UUID materialId) {
        List<SupplierMaterial> all = supplierMaterialRepository
            .search(null, materialId, "", PageRequest.of(0, 10))
            .getContent();
        return all.stream()
            .filter(sm -> "ACTIVE".equals(sm.getStatus()))
            .sorted((a, b) -> Boolean.compare(b.isPreferred(), a.isPreferred()))
            .map(SupplierMaterial::getPurchasePrice)
            .filter(p -> p != null)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }
}
