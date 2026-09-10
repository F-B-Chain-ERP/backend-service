package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ProductMapper;
import com.erp.backend_service.repository.CategoryRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.service.ProductService;
import com.erp.core.domain.Category;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.request.menu.CreateProductRequest;
import com.erp.core.dto.request.menu.UpdateProductRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.CreateProductResponse;
import com.erp.core.dto.response.menu.ProductDetailResponse;
import com.erp.core.dto.response.menu.ProductResponse;
import com.erp.core.dto.response.menu.ProductSalesResponse;
import com.erp.core.dto.response.menu.ProductVariantResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductVariantRepository productVariantRepository,
            ProductMapper productMapper
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.productMapper = productMapper;
    }

    @Override
    public PageResponse<ProductResponse> list(
            int page,
            int size,
            String search,
            UUID categoryId,
            String status,
            Boolean isFeatured,
            Boolean isBestSeller
    ) {
       log.info("Get-list product");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> pageResult = productRepository.search(search, categoryId, status, isFeatured, isBestSeller, pageable);
        List<Product> products = pageResult.getContent();

        // Tránh lỗi N+1: Gom toàn bộ categoryId duy nhất, bulk-fetch bằng một câu query duy nhất
        List<UUID> catIds = distinctNonNull(products, Product::getCategoryId);
        Map<UUID, Category> categoryMap = toMap(categoryRepository.findAllById(catIds), Category::getId);

        List<ProductResponse> content = products.stream()
                .map(p -> {
                    Category cat = categoryMap.get(p.getCategoryId());
                    String categoryName = cat != null ? cat.getName() : null;
                    return productMapper.toAdminResponse(p, categoryName);
                })
                .toList();

        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                content
        );
    }

    @Override
    public PageResponse<ProductSalesResponse> listForSales(
            int page,
            int size,
            String search,
            UUID categoryId,
            Boolean isFeatured
    ) {
        log.info("Get-list product for sale");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<Product> pageResult = productRepository.findActiveForSales(search, categoryId, isFeatured, pageable);
        List<Product> products = pageResult.getContent();

        // Tránh lỗi N+1: Gom toàn bộ categoryId duy nhất, bulk-fetch bằng một câu query duy nhất
        List<UUID> catIds = distinctNonNull(products, Product::getCategoryId);
        Map<UUID, Category> categoryMap = toMap(categoryRepository.findAllById(catIds), Category::getId);

        List<ProductSalesResponse> content = products.stream()
                .map(p -> {
                    Category cat = categoryMap.get(p.getCategoryId());
                    String categoryName = cat != null ? cat.getName() : null;
                    return productMapper.toSalesResponse(p, categoryName);
                })
                .toList();

        return new PageResponse<>(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                content
        );
    }

    @Override
    public ProductDetailResponse get(UUID id) {
        log.info("Get-product by id");
        Product product = findById(id);
        if ("DELETED".equalsIgnoreCase(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND);
        }
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(Category::getName)
                .orElse(null);
        List<ProductVariant> variants = productVariantRepository.findByProductIdOrderByDisplayOrderAsc(product.getId());
        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(productMapper::toVariantResponse)
                .toList();
        return productMapper.toDetailResponse(product, categoryName, variantResponses);
    }

    @Override
    public ProductDetailResponse getDetailForSales(UUID id) {
        log.info("Get-product by id for sale");
        Product product = findById(id);
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND);
        }
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(Category::getName)
                .orElse(null);
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(product.getId(), "ACTIVE");
        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(productMapper::toVariantResponse)
                .toList();
        return productMapper.toDetailResponse(product, categoryName, variantResponses);
    }

    private <T> List<UUID> distinctNonNull(List<T> list, Function<T, UUID> idFn) {
        return list.stream()
                .map(idFn)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private <T> Map<UUID, T> toMap(List<T> items, Function<T, UUID> idFn) {
        return items.stream().collect(Collectors.toMap(idFn, Function.identity(), (a, b) -> a));
    }

    @Override
    @Transactional
    public CreateProductResponse create(CreateProductRequest request) {
        log.info("Create product");
        // 1. Validate category
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_CATEGORY_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(category.getStatus())) {
            throw new BaseException(ErrorCode.MENU_400_CATEGORY_INACTIVE);
        }

        // 2. Validate code uniqueness (case-insensitive, normalize to upper)
        String normalizedCode = request.code().trim().toUpperCase();
        if (productRepository.existsByCode(normalizedCode)) {
            throw new BaseException(ErrorCode.MENU_409_PRODUCT_CODE_EXISTED);
        }

        // 3. Build entity
        Product product = new Product();
        product.setCategoryId(request.categoryId());
        product.setCode(normalizedCode);
        product.setName(trimOrNull(request.name()));
        product.setDescription(trimOrNull(request.description()));
        product.setImageUrl(trimOrNull(request.imageUrl()));
        product.setBasePrice(request.basePrice());
        product.setFeatured(Boolean.TRUE.equals(request.isFeatured()));
        product.setBestSeller(Boolean.TRUE.equals(request.isBestSeller()));
        product.setCombo(Boolean.TRUE.equals(request.isCombo()));
        product.setStatus("ACTIVE");

        Product saved = productRepository.save(product);
        return new CreateProductResponse(
                saved.getId() != null ? saved.getId().toString() : null,
                saved.getCode(),
                saved.getName(),
                saved.getBasePrice(),
                saved.getStatus()
        );
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        log.info("Update product");
        Product product = findById(id);
        if ("DELETED".equalsIgnoreCase(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND);
        }

        // 1. Validate category
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_CATEGORY_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(category.getStatus()) && !product.getCategoryId().equals(request.categoryId())) {
            throw new BaseException(ErrorCode.MENU_400_CATEGORY_INACTIVE);
        }

        // 2. Validate code uniqueness (case-insensitive, normalize to upper)
        String normalizedCode = request.code().trim().toUpperCase();
        if (!product.getCode().equalsIgnoreCase(normalizedCode)
                && productRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new BaseException(ErrorCode.MENU_409_PRODUCT_CODE_EXISTED);
        }

        // 3. Update entity fields
        product.setCategoryId(request.categoryId());
        product.setCode(normalizedCode);
        product.setName(trimOrNull(request.name()));
        product.setDescription(trimOrNull(request.description()));
        product.setImageUrl(trimOrNull(request.imageUrl()));
        product.setBasePrice(request.basePrice());
        if (request.preparationMinutes() != null) {
            product.setPreparationMinutes(request.preparationMinutes());
        }
        if (request.isFeatured() != null) {
            product.setFeatured(request.isFeatured());
        }
        if (request.isBestSeller() != null) {
            product.setBestSeller(request.isBestSeller());
        }
        if (request.isCombo() != null) {
            product.setCombo(request.isCombo());
        }
        if (request.availableIceLevels() != null) {
            product.setAvailableIceLevels(request.availableIceLevels().trim());
        }
        if (request.availableSugarLevels() != null) {
            product.setAvailableSugarLevels(request.availableSugarLevels().trim());
        }
        if (request.status() != null && !request.status().trim().isEmpty()) {
            String normalizedStatus = request.status().trim().toUpperCase();
            if (!"ACTIVE".equals(normalizedStatus) && !"INACTIVE".equals(normalizedStatus)) {
                throw new BaseException(ErrorCode.INVALID_REQUEST);
            }
            product.setStatus(normalizedStatus);
        }

        Product saved = productRepository.save(product);
        return productMapper.toAdminResponse(saved, category.getName());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Delete product");
        Product product = findById(id);
        if ("DELETED".equalsIgnoreCase(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND);
        }
        // Xóa mềm: Chuyển trạng thái sang DELETED
        product.setStatus("DELETED");
        productRepository.save(product);
    }

    private Product findById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

