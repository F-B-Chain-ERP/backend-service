package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.AccountMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.service.AccountService;
import com.erp.core.domain.Account;
import com.erp.core.dto.auth.AccountResponse;
import com.erp.core.dto.auth.CreateAccountRequest;
import com.erp.core.dto.auth.ResetPasswordRequest;
import com.erp.core.dto.auth.UpdateAccountRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.enums.AuthProvider;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Triển khai {@link AccountService}: quản lý tài khoản nội bộ do admin thực hiện
 * (tạo, xem, tìm kiếm, cập nhật, vô hiệu hóa, đặt lại mật khẩu). Chỉ tài khoản
 * nội bộ (ACCOUNT) mới được phép gọi các nghiệp vụ này.
 */
@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountRevocationService revocationService;
    private final Duration accessTokenLifetime;

    public AccountServiceImpl(AccountRepository accountRepository,
                              AccountMapper accountMapper,
                              PasswordEncoder passwordEncoder,
                              AccountRevocationService revocationService,
                              @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.revocationService = revocationService;
        this.accessTokenLifetime = Duration.ofSeconds(accessTokenExpiry);
    }

    /** {@inheritDoc} */
    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {
        assertInternalAdmin();
        if (accountRepository.findByUsername(request.username()).isPresent()) {
            throw new BaseException(ErrorCode.USER_EXISTED);
        }
        if (request.email() != null && accountRepository.findByEmail(request.email()).isPresent()) {
            throw new BaseException(ErrorCode.USER_EXISTED);
        }
        if (request.phone() != null && accountRepository.existsByPhone(request.phone())) {
            throw new BaseException(ErrorCode.USER_EXISTED);
        }

        Account account = new Account();
        account.setUsername(request.username());
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setFullName(request.fullName());
        account.setEmail(request.email());
        account.setPhone(request.phone());
        account.setPrimaryBranchId(request.primaryBranchId());
        account.setAuthProvider(request.authProvider() != null ? request.authProvider() : AuthProvider.LOCAL);
        account.setHasLocalPassword(true);
        account.setSystemProtected(false);
        account.setStatus(EntityStatus.ACTIVE);
        account = accountRepository.save(account);
        return accountMapper.toResponse(account);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        assertInternalAdmin();
        return accountMapper.toResponse(findById(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAccounts(int page, int size, String search) {
        assertInternalAdmin();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending());
        Page<Account> accountPage = accountRepository.search(
                StringUtils.hasText(search) ? search.trim() : null, pageable);
        return new PageResponse<>(
                accountPage.getNumber(),
                accountPage.getSize(),
                accountPage.getTotalElements(),
                accountPage.getTotalPages(),
                accountPage.getContent().stream().map(accountMapper::toResponse).toList()
        );
    }

    /** {@inheritDoc} */
    @Override
    public AccountResponse updateAccount(UUID id, UpdateAccountRequest request) {
        assertInternalAdmin();
        Account account = findById(id);
        verifyCanModify(account);

        if (request.fullName() != null) {
            account.setFullName(request.fullName());
        }
        if (request.email() != null && !Objects.equals(account.getEmail(), request.email())) {
            if (accountRepository.existsByEmailAndIdNot(request.email(), id)) {
                throw new BaseException(ErrorCode.USER_EXISTED);
            }
            account.setEmail(request.email());
        }
        if (request.phone() != null && !Objects.equals(account.getPhone(), request.phone())) {
            if (accountRepository.existsByPhoneAndIdNot(request.phone(), id)) {
                throw new BaseException(ErrorCode.USER_EXISTED);
            }
            account.setPhone(request.phone());
        }
        if (request.avatarUrl() != null) {
            account.setAvatarUrl(request.avatarUrl());
        }
        if (request.primaryBranchId() != null) {
            account.setPrimaryBranchId(request.primaryBranchId());
        }
        if (request.status() != null) {
            account.setStatus(request.status());
        }
        return accountMapper.toResponse(accountRepository.save(account));
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAccount(UUID id) {
        assertInternalAdmin();
        Account account = findById(id);
        verifyCanModify(account);
        account.setStatus(EntityStatus.INACTIVE);
        accountRepository.save(account);
        revocationService.revokeAccount(id, accessTokenLifetime);
    }

    /** {@inheritDoc} */
    @Override
    public AccountResponse resetPassword(UUID id, ResetPasswordRequest request) {
        assertInternalAdmin();
        Account account = findById(id);
        verifyCanModify(account);
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setHasLocalPassword(true);
        Account saved = accountRepository.save(account);
        revocationService.revokeAccount(id, accessTokenLifetime);
        return accountMapper.toResponse(saved);
    }

    /** Lấy tài khoản theo id, ném lỗi nếu không tồn tại. */
    private Account findById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /** Ngăn thay đổi/xóa tài khoản được bảo vệ của hệ thống (ví dụ: root admin). */
    private void verifyCanModify(Account account) {
        if (account.isSystemProtected()) {
            throw new BaseException(ErrorCode.CANNOT_MODIFY_ADMIN);
        }
    }

    /** Chỉ tài khoản nội bộ (ACCOUNT) mới được quản lý tài khoản. */
    private void assertInternalAdmin() {
        if (SecurityUtils.getCurrentPrincipalType().orElse(null) != PrincipalType.ACCOUNT) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }
}
