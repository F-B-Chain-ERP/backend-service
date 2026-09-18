package com.erp.backend_service.security;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.core.enums.PrincipalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockUser(PrincipalType principalType, UUID principalId) {
        CustomUserDetails userDetails = new CustomUserDetails(
                principalType,
                principalId,
                "testuser",
                "password",
                true,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Instant.now()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Test
    @DisplayName("requireCustomerId should return UUID when principal is CUSTOMER")
    void requireCustomerId_WhenCustomer_ReturnsCustomerId() {
        UUID customerId = UUID.randomUUID();
        mockUser(PrincipalType.CUSTOMER, customerId);

        UUID result = SecurityUtils.requireCustomerId();
        assertEquals(customerId, result);
    }

    @Test
    @DisplayName("requireCustomerId should throw UNAUTHORIZED when principal is ACCOUNT")
    void requireCustomerId_WhenAccount_ThrowsUnauthorized() {
        mockUser(PrincipalType.ACCOUNT, UUID.randomUUID());

        BaseException ex = assertThrows(BaseException.class, SecurityUtils::requireCustomerId);
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertEquals("Chỉ CUSTOMER được truy cập.", ex.getMessage());
    }

    @Test
    @DisplayName("requireCustomerId should throw UNAUTHORIZED when not authenticated")
    void requireCustomerId_WhenUnauthenticated_ThrowsUnauthorized() {
        BaseException ex = assertThrows(BaseException.class, SecurityUtils::requireCustomerId);
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
}
