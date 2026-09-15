package com.erp.backend_service.service;

import com.erp.core.dto.request.pos.AddCartItemRequest;
import com.erp.core.dto.request.pos.UpdateCartItemRequest;
import com.erp.core.dto.response.pos.CartMutationResponse;
import com.erp.core.dto.response.pos.CartResponse;

import java.util.UUID;

public interface CartService {
    CartResponse getCart(UUID branchId, String sessionToken);

    CartMutationResponse addItem(AddCartItemRequest request);

    CartMutationResponse updateItem(UUID itemId, UpdateCartItemRequest request);

    CartMutationResponse deleteItem(UUID itemId);
}
