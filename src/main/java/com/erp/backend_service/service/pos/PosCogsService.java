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
