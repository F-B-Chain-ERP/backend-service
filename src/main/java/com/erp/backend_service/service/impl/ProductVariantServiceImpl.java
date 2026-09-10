package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ProductMapper;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.service.ProductVariantService;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.request.menu.CreateProductVariantRequest;
import com.erp.core.dto.request.menu.SyncProductVariantsRequest;
import com.erp.core.dto.request.menu.UpdateProductVariantRequest;
import com.erp.core.dto.response.menu.ProductVariantResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hiện thực nghiệp vụ quản lý các biến thể / kích cỡ của sản phẩm (Size S, M, L...).
 */
@Service
public class ProductVariantServiceImpl implements ProductVariantService {

    private static final Logger log = LoggerFactory.getLogger(ProductVariantServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductMapper productMapper;

    public ProductVariantServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductMapper productMapper
    ) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "productVariants", key = "#productId")
    public List<ProductVariantResponse> getVariantsByProductId(UUID productId) {
        log.info("Get variant by product id");
        ensureProductExists(productId);
        List<ProductVariant> variants = productVariantRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        return variants.stream()
                .map(productMapper::toVariantResponse)
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "productVariants", key = "#productId"),
            @CacheEvict(value = "productDetail", key = "#productId"),
            @CacheEvict(value = "salesProductDetail", key = "#productId"),
            @CacheEvict(value = "salesProducts", allEntries = true)
    })
    public ProductVariantResponse create(UUID productId, CreateProductVariantRequest request) {
        log.info("Create product variant");
        ensureProductExists(productId);

        String normalizedCode = request.variantCode().trim().toUpperCase();
        if (productVariantRepository.existsByProductIdAndVariantCodeIgnoreCase(productId, normalizedCode)) {
            throw new BaseException(ErrorCode.MENU_409_VARIANT_CODE_EXISTED);
        }

        ProductVariant variant = new ProductVariant();
        variant.setProductId(productId);
        variant.setVariantCode(normalizedCode);
        variant.setVariantName(request.variantName().trim());
        variant.setSizeLabel(request.sizeLabel().trim());
        variant.setPriceDelta(request.priceDelta());
        variant.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        variant.setStatus("ACTIVE");

        ProductVariant saved = productVariantRepository.save(variant);
        return productMapper.toVariantResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "productVariants", key = "#productId"),
            @CacheEvict(value = "productDetail", key = "#productId"),
            @CacheEvict(value = "salesProductDetail", key = "#productId"),
            @CacheEvict(value = "salesProducts", allEntries = true)
    })
    public ProductVariantResponse update(UUID productId, UUID variantId, UpdateProductVariantRequest request) {
        log.info("Update product variant");
        ensureProductExists(productId);

        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_VARIANT_NOT_FOUND));

        String normalizedCode = request.variantCode().trim().toUpperCase();
        if (!variant.getVariantCode().equalsIgnoreCase(normalizedCode)
                && productVariantRepository.existsByProductIdAndVariantCodeIgnoreCaseAndIdNot(productId, normalizedCode, variantId)) {
            throw new BaseException(ErrorCode.MENU_409_VARIANT_CODE_EXISTED);
        }

        variant.setVariantCode(normalizedCode);
        variant.setVariantName(request.variantName().trim());
        variant.setSizeLabel(request.sizeLabel().trim());
        variant.setPriceDelta(request.priceDelta());
        if (request.displayOrder() != null) {
            variant.setDisplayOrder(request.displayOrder());
        }
        if (request.status() != null && !request.status().trim().isEmpty()) {
            String status = request.status().trim().toUpperCase();
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw new BaseException(ErrorCode.INVALID_REQUEST);
            }
            variant.setStatus(status);
        }

        ProductVariant saved = productVariantRepository.save(variant);
        return productMapper.toVariantResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "productVariants", key = "#productId"),
            @CacheEvict(value = "productDetail", key = "#productId"),
            @CacheEvict(value = "salesProductDetail", key = "#productId"),
            @CacheEvict(value = "salesProducts", allEntries = true)
    })
    public void delete(UUID productId, UUID variantId) {
        log.info("Delete product variant");
        ensureProductExists(productId);

        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_VARIANT_NOT_FOUND));

        try {
            productVariantRepository.delete(variant);
            productVariantRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BaseException(ErrorCode.MENU_400_VARIANT_IN_USE);
        }
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "productVariants", key = "#productId"),
            @CacheEvict(value = "productDetail", key = "#productId"),
            @CacheEvict(value = "salesProductDetail", key = "#productId"),
            @CacheEvict(value = "salesProducts", allEntries = true)
    })
    public List<ProductVariantResponse> syncVariants(UUID productId, SyncProductVariantsRequest request) {
        log.info("Sync variants");
        ensureProductExists(productId);

        List<SyncProductVariantsRequest.VariantItemRequest> incomingItems =
                request.variants() != null ? request.variants() : Collections.emptyList();

        // Kiểm tra tính duy nhất của variantCode trong payload đồng bộ gửi lên
        Set<String> seenCodes = new HashSet<>();
        for (SyncProductVariantsRequest.VariantItemRequest item : incomingItems) {
            String code = item.variantCode().trim().toUpperCase();
            if (!seenCodes.add(code)) {
                throw new BaseException(ErrorCode.MENU_409_VARIANT_CODE_EXISTED,
                        "Mã biến thể '" + code + "' bị trùng lặp trong danh sách đồng bộ.");
            }
        }

        List<ProductVariant> currentVariants = productVariantRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        Map<UUID, ProductVariant> currentMap = currentVariants.stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Set<UUID> incomingIds = incomingItems.stream()
                .map(SyncProductVariantsRequest.VariantItemRequest::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 1. Xóa các biến thể đã có nhưng không còn nằm trong request gửi lên.
        // Gom 1 lần, flush 1 lần cuối (trước đây flush từng dòng -> N round-trip).
        // Không đổi DB, chỉ đổi cách ghi để tận dụng hibernate.jdbc.batch_size=100.
        List<ProductVariant> toDelete = new ArrayList<>();
        for (ProductVariant existing : currentVariants) {
            if (existing.getId() != null && !incomingIds.contains(existing.getId())) {
                toDelete.add(existing);
            }
        }
        if (!toDelete.isEmpty()) {
            try {
                productVariantRepository.deleteAll(toDelete);
            } catch (DataIntegrityViolationException e) {
                throw new BaseException(ErrorCode.MENU_400_VARIANT_IN_USE,
                        "Biến thể đang được sử dụng, không thể xóa bỏ khỏi danh sách.");
            }
        }

        // 2. Thêm mới hoặc cập nhật từng item.
        // Giữ save() từng entity (không đổi signature repo), nhưng chỉ flush 1 lần cuối
        // để Hibernate gộp JDBC batch (batch_size=100). Thu thập kết quả để trả về,
        // bỏ SELECT lại toàn bộ lần 2.
        List<ProductVariant> managed = new ArrayList<>(incomingItems.size());
        for (SyncProductVariantsRequest.VariantItemRequest item : incomingItems) {
            String normalizedCode = item.variantCode().trim().toUpperCase();
            if (item.id() != null && currentMap.containsKey(item.id())) {
                // Update
                ProductVariant existing = currentMap.get(item.id());
                existing.setVariantCode(normalizedCode);
                existing.setVariantName(item.variantName().trim());
                existing.setSizeLabel(item.sizeLabel().trim());
                existing.setPriceDelta(item.priceDelta());
                existing.setDisplayOrder(item.displayOrder() != null ? item.displayOrder() : 0);
                existing.setStatus(item.status() != null ? item.status().trim().toUpperCase() : "ACTIVE");
                ProductVariant savedExisting = productVariantRepository.save(existing);
                managed.add(savedExisting != null ? savedExisting : existing);
            } else {
                // Create
                ProductVariant newVariant = new ProductVariant();
                newVariant.setProductId(productId);
                newVariant.setVariantCode(normalizedCode);
                newVariant.setVariantName(item.variantName().trim());
                newVariant.setSizeLabel(item.sizeLabel().trim());
                newVariant.setPriceDelta(item.priceDelta());
                newVariant.setDisplayOrder(item.displayOrder() != null ? item.displayOrder() : 0);
                newVariant.setStatus(item.status() != null ? item.status().trim().toUpperCase() : "ACTIVE");
                ProductVariant savedNew = productVariantRepository.save(newVariant);
                managed.add(savedNew != null ? savedNew : newVariant);
            }
        }

        // 1 flush duy nhất cho cả xóa + thêm/sửa.
        productVariantRepository.flush();

        managed.sort(Comparator.comparingInt(ProductVariant::getDisplayOrder));
        return managed.stream()
                .map(productMapper::toVariantResponse)
                .toList();
    }

    private Product ensureProductExists(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
        if ("DELETED".equalsIgnoreCase(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND);
        }
        return product;
    }
}
