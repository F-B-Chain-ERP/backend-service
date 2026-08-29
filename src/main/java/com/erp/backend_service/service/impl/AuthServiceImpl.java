package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BadRequestException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.AuthMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.CustomerRepository;
import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.CustomUserDetailsService;
import com.erp.backend_service.security.JwtProvider;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.AuditService;
import com.erp.backend_service.service.AuthService;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.service.MailService;
import com.erp.backend_service.service.OtpService;
import com.erp.core.dto.auth.*;
import com.erp.core.enums.OtpPurpose;
import com.erp.backend_service.service.PermissionService;
import com.erp.backend_service.service.RefreshTokenService;
import com.erp.backend_service.util.audit.AuditAction;
import com.erp.backend_service.util.audit.AuditEvent;
import com.erp.backend_service.util.audit.AuditModule;
import com.erp.backend_service.util.audit.AuditTargetType;
import com.erp.core.domain.Account;
import com.erp.core.domain.Customer;
import com.erp.core.enums.AuthProvider;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import com.erp.core.enums.ScopeType;
import com.erp.backend_service.security.oauth2.GoogleTokenVerifier;
import com.erp.backend_service.security.oauth2.GoogleUserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Triển khai {@link AuthService}: xác thực đăng nhập cho cả tài khoản nội bộ
 * (ACCOUNT) và khách hàng (CUSTOMER), cấp phát / làm mới / thu hồi JWT dựa trên
 * refresh token lưu trên DB (mô hình đa hình), đồng thời ghi nhận audit log.
 */
@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService accountUserDetailsService;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final AuthMapper authMapper;
    private final PermissionService permissionService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AccountRevocationService accountRevocationService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final MailService mailService;
    private final OtpService otpService;
    private final String brandName;
    private final Duration accessTokenLifetime;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                            JwtProvider jwtProvider,
                            CustomUserDetailsService accountUserDetailsService,
                            AccountRepository accountRepository,
                            CustomerRepository customerRepository,
                            AuditService auditService,
                            AuthMapper authMapper,
                            PermissionService permissionService,
                            RefreshTokenService refreshTokenService,
                            PasswordEncoder passwordEncoder,
                            GoogleTokenVerifier googleTokenVerifier,
                            MailService mailService,
                            AccountRevocationService accountRevocationService,
                            OtpService otpService,
                            @Value("${app.mail.from-name}") String brandName,
                            @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry) {
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
        this.accountUserDetailsService = accountUserDetailsService;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.authMapper = authMapper;
        this.permissionService = permissionService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.googleTokenVerifier = googleTokenVerifier;
        this.mailService = mailService;
        this.accountRevocationService = accountRevocationService;
        this.otpService = otpService;
        this.brandName = brandName;
        this.accessTokenLifetime = Duration.ofSeconds(accessTokenExpiry);
    }

    /** {@inheritDoc} */
    @Override
    public AuthResponse login(LoginRequest request) {
        PrincipalType type = resolvePrincipalType(request.type(), request.usernameOrEmail());
        CustomUserDetails userDetails;
        try {
            userDetails = authenticate(type, request.usernameOrEmail(), request.password());
        } catch (BadCredentialsException exception) {
            UUID actorId = resolveActorId(type, request.usernameOrEmail());
            auditLogin(type, actorId, AuditAction.LOGIN_FAILED, Map.of("reason", ErrorCode.BAD_CREDENTIALS.getCode()));
            throw exception;
        }

        if (type == PrincipalType.ACCOUNT) {
            Account account = accountRepository.findById(userDetails.getPrincipalId()).orElseThrow();
            account.setLastLoginAt(Instant.now());
            accountRepository.save(account);
            permissionService.saveSnapshot(account.getId(), permissionService.snapshotFromDetails(userDetails));
            UUID autoBranch = resolveAutoBranch(userDetails.getScopes());
            if (autoBranch != null) {
                userDetails = CustomUserDetails.withBranch(userDetails, autoBranch);
            }
            boolean requiresScope = userDetails.getBranchId() == null
                && userDetails.getScopes().stream().noneMatch(s -> s.scopeType() == ScopeType.ALL_SYSTEM);
            auditLogin(type, account.getId(), AuditAction.LOGIN_SUCCESS, Map.of("requiresScopeAssignment", requiresScope));
            return issueTokens(userDetails, requiresScope,
                    null);
        } else {
            Customer customer = customerRepository.findById(userDetails.getPrincipalId()).orElseThrow();
            customer.setLastLoginAt(Instant.now());
            customerRepository.save(customer);
            auditLogin(type, customer.getId(), AuditAction.LOGIN_SUCCESS, Map.of());
            if (!customer.isEmailVerified()) {
                // Đăng nhập thành công nhưng email chưa xác thực -> yêu cầu xác thực OTP.
                String verifyToken = jwtProvider.generateEmailVerifyToken(
                        PrincipalType.CUSTOMER, customer.getId(), customer.getEmail());
                return authMapper.toResponse(null, null, PrincipalType.CUSTOMER,
                        authMapper.toCustomerResponse(customer), false, true, verifyToken);
            }
            return issueTokens(userDetails, false, authMapper.toCustomerResponse(customer));
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse registerCustomer(RegisterCustomerRequest request) {
        if (request.phone() != null && customerRepository.existsByPhone(request.phone())) {
            throw new BadRequestException(ErrorCode.PHONE_EXISTED);
        }

        if (request.username() != null && customerRepository.existsByUsername(request.username())) {
            throw new BadRequestException(ErrorCode.USER_EXISTED);
        }

        if (request.email() != null && customerRepository.existsByEmail(request.email())) {
            Customer existing = customerRepository.findByEmail(request.email()).orElseThrow();
            boolean isOauth2 = existing.getAuthProvider() != AuthProvider.LOCAL;
            if (isOauth2) {
                // Gmail/OAuth2 đã tồn tại trong DB -> auto pass: liên kết mật khẩu
                // local và đánh dấu email đã xác thực (Google đã verify email).
                existing.setPassword(passwordEncoder.encode(request.password()));
                existing.setHasLocalPassword(true);
                existing.setStatus(EntityStatus.ACTIVE);
                existing.setEmailVerified(true);
                existing.setLastLoginAt(Instant.now());
                existing = customerRepository.save(existing);

                CustomUserDetails userDetails = CustomUserDetails.fromCustomer(existing);
                auditLogin(PrincipalType.CUSTOMER, existing.getId(), AuditAction.LOGIN_SUCCESS,
                        Map.of("registered", true, "oauth2_linked", true));
                sendWelcomeEmail(existing);
                return issueTokens(userDetails, false, authMapper.toCustomerResponse(existing));
            }
            throw new BadRequestException(ErrorCode.EMAIL_EXISTED);
        }

        Customer customer = new Customer();
        customer.setCustomerCode(generateCustomerCode());
        customer.setUsername(request.username());
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setAuthProvider(AuthProvider.LOCAL);
        customer.setHasLocalPassword(true);
        customer.setPassword(passwordEncoder.encode(request.password()));
        customer.setEmailVerified(false);
        customer.setStatus(EntityStatus.ACTIVE);
        customer = customerRepository.save(customer);

        CustomUserDetails userDetails = CustomUserDetails.fromCustomer(customer);
        auditLogin(PrincipalType.CUSTOMER, customer.getId(), AuditAction.LOGIN_SUCCESS,
                Map.of("registered", true, "email_verification_required", true));

        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            otpService.generateAndSendOtp(customer.getId(), customer.getEmail(),
                    customer.getFullName(), OtpPurpose.REGISTRATION);
        }

        String verifyToken = jwtProvider.generateEmailVerifyToken(
                PrincipalType.CUSTOMER, customer.getId(), customer.getEmail());
        return authMapper.toResponse(null, null, PrincipalType.CUSTOMER,
                authMapper.toCustomerResponse(customer), false, true, verifyToken);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse verifyEmailOtp(VerifyOtpRequest request) {
        String verifyToken = request.verifyToken();
        if (!jwtProvider.isEmailVerifyToken(verifyToken)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        UUID customerId = jwtProvider.extractPrincipalId(verifyToken);
        String email = jwtProvider.extractEmail(verifyToken);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
        if (!customer.isEmailVerified()) {
            otpService.verifyOtp(customerId, request.otp(), OtpPurpose.REGISTRATION);
            customer.setEmailVerified(true);
            customer = customerRepository.save(customer);
            auditLogin(PrincipalType.CUSTOMER, customerId, AuditAction.LOGIN_SUCCESS,
                    Map.of("email_verified", true));
        }

        CustomUserDetails userDetails = CustomUserDetails.fromCustomer(customer);
        return issueTokens(userDetails, false, authMapper.toCustomerResponse(customer));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse resendRegistrationOtp(ResendOtpRequest request) {
        String verifyToken = request.verifyToken();
        if (!jwtProvider.isEmailVerifyToken(verifyToken)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        UUID customerId = jwtProvider.extractPrincipalId(verifyToken);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
        otpService.generateAndSendOtp(customer.getId(), customer.getEmail(),
                customer.getFullName(), OtpPurpose.REGISTRATION);

        String newVerifyToken = jwtProvider.generateEmailVerifyToken(
                PrincipalType.CUSTOMER, customerId, customer.getEmail());
        return authMapper.toResponse(null, null, PrincipalType.CUSTOMER,
                authMapper.toCustomerResponse(customer), false, true, newVerifyToken);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse forgotPassword(ForgotPasswordRequest request) {
        PrincipalType type = request.type() != null ? request.type() : PrincipalType.CUSTOMER;
        String email = request.email();
        if (email == null || email.isBlank()) {
            throw new BadRequestException(ErrorCode.EMAIL_REQUIRED);
        }

        UUID principalId;
        String fullName;
        if (type == PrincipalType.ACCOUNT) {
            Account account = accountRepository.findByEmail(email)
                    .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
            principalId = account.getId();
            fullName = account.getFullName();
        } else {
            Customer customer = customerRepository.findByEmail(email)
                    .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
            principalId = customer.getId();
            fullName = customer.getFullName();
        }

        otpService.generateAndSendOtp(principalId, email, fullName, OtpPurpose.PASSWORD_RESET);
        String resetToken = jwtProvider.generatePasswordResetToken(type, principalId, email);
        auditLogin(type, principalId, AuditAction.LOGIN_FAILED,
                Map.of("action", "forgot_password_requested"));
        return authMapper.toResponse(null, null, type, null, false, false, resetToken);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse resetPassword(ResetPasswordOtpRequest request) {
        String resetToken = request.resetToken();
        if (!jwtProvider.isPasswordResetToken(resetToken)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        PrincipalType type = jwtProvider.extractPrincipalType(resetToken);
        UUID principalId = jwtProvider.extractPrincipalId(resetToken);
        String email = jwtProvider.extractEmail(resetToken);

        otpService.verifyOtp(principalId, request.otp(), OtpPurpose.PASSWORD_RESET);

        if (type == PrincipalType.ACCOUNT) {
            Account account = accountRepository.findById(principalId)
                    .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
            account.setPassword(passwordEncoder.encode(request.newPassword()));
            accountRepository.save(account);
            accountRevocationService.revokeAccount(principalId, accessTokenLifetime);
            auditLogin(type, principalId, AuditAction.LOGIN_SUCCESS,
                    Map.of("action", "password_reset"));
            CustomUserDetails userDetails =
                    (CustomUserDetails) accountUserDetailsService.loadUserByUsername(account.getUsername());
            permissionService.saveSnapshot(principalId, permissionService.snapshotFromDetails(userDetails));
            return issueTokens(userDetails, false, null);
        } else {
            Customer customer = customerRepository.findById(principalId)
                    .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
            customer.setPassword(passwordEncoder.encode(request.newPassword()));
            customer.setHasLocalPassword(true);
            customerRepository.save(customer);
            auditLogin(type, principalId, AuditAction.LOGIN_SUCCESS,
                    Map.of("action", "password_reset"));
            CustomUserDetails userDetails = CustomUserDetails.fromCustomer(customer);
            return issueTokens(userDetails, false, authMapper.toCustomerResponse(customer));
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        CustomUserDetails current = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new BadRequestException(ErrorCode.UNAUTHENTICATED));
        PrincipalType type = current.getPrincipalType();
        UUID principalId = current.getPrincipalId();

        if (type == PrincipalType.ACCOUNT) {
            Account account = accountRepository.findById(principalId)
                    .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
            if (account.getPassword() == null
                    || !passwordEncoder.matches(request.oldPassword(), account.getPassword())) {
                throw new BadRequestException(ErrorCode.BAD_CREDENTIALS);
            }
            account.setPassword(passwordEncoder.encode(request.newPassword()));
            accountRepository.save(account);
            accountRevocationService.revokeAccount(principalId, accessTokenLifetime);
            CustomUserDetails userDetails =
                    (CustomUserDetails) accountUserDetailsService.loadUserByUsername(account.getUsername());
            permissionService.saveSnapshot(principalId, permissionService.snapshotFromDetails(userDetails));
            auditLogin(type, principalId, AuditAction.LOGIN_SUCCESS,
                    Map.of("action", "change_password"));
            return issueTokens(userDetails, false, null);
        } else {
            Customer customer = customerRepository.findById(principalId)
                    .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
            // Khách hàng chưa có mật khẩu cục bộ (đăng nhập Google) được đặt mật khẩu mới
            // mà không cần mật khẩu cũ; ngược lại vẫn yêu cầu xác thực mật khẩu hiện tại.
            if (customer.isHasLocalPassword()
                    && !passwordEncoder.matches(request.oldPassword(), customer.getPassword())) {
                throw new BadRequestException(ErrorCode.BAD_CREDENTIALS);
            }
            customer.setPassword(passwordEncoder.encode(request.newPassword()));
            customer.setHasLocalPassword(true);
            customerRepository.save(customer);
            auditLogin(type, principalId, AuditAction.LOGIN_SUCCESS,
                    Map.of("action", "change_password"));
            CustomUserDetails userDetails = CustomUserDetails.fromCustomer(customer);
            return issueTokens(userDetails, false, authMapper.toCustomerResponse(customer));
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleOAuth2Request request) {
        GoogleUserInfo info = googleTokenVerifier.verify(request.idToken());
        if (!info.emailVerified() || info.email() == null) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }

        Customer customer = customerRepository.findByProviderId(info.sub())
                .or(() -> customerRepository.findByEmail(info.email()))
                .orElse(null);

        if (customer == null) {
            customer = new Customer();
            customer.setCustomerCode(generateCustomerCode());
            customer.setFullName(info.name());
            customer.setEmail(info.email());
            customer.setAvatarUrl(info.picture());
            customer.setAuthProvider(AuthProvider.GOOGLE);
            customer.setProviderId(info.sub());
            customer.setHasLocalPassword(false);
            customer.setStatus(EntityStatus.ACTIVE);
            customer = customerRepository.save(customer);
        } else {
            if (customer.getStatus() != EntityStatus.ACTIVE) {
                throw new BadRequestException(ErrorCode.ACCOUNT_DISABLED);
            }
            boolean changed = false;
            if (customer.getProviderId() == null) {
                customer.setProviderId(info.sub());
                customer.setAuthProvider(AuthProvider.GOOGLE);
                changed = true;
            }
            if (customer.getAvatarUrl() == null && info.picture() != null) {
                customer.setAvatarUrl(info.picture());
                changed = true;
            }
            // Email đã được Google xác thực -> auto pass, không cần OTP.
            if (!customer.isEmailVerified()) {
                customer.setEmailVerified(true);
                changed = true;
            }
            if (changed) {
                customer = customerRepository.save(customer);
            }
        }

        // Khách hàng đăng nhập bằng Google luôn được coi là email đã xác thực.
        if (!customer.isEmailVerified()) {
            customer.setEmailVerified(true);
        }
        customer.setLastLoginAt(Instant.now());
        customerRepository.save(customer);

        CustomUserDetails userDetails = CustomUserDetails.fromCustomer(customer);
        auditLogin(PrincipalType.CUSTOMER, customer.getId(), AuditAction.LOGIN_SUCCESS,
                Map.of("provider", "GOOGLE"));
        sendWelcomeEmail(customer);
        return issueTokens(userDetails, false, authMapper.toCustomerResponse(customer));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String currentToken = request.refreshToken();
        if (!jwtProvider.validateToken(currentToken) || !jwtProvider.isRefreshToken(currentToken)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }

        PrincipalType type = jwtProvider.extractPrincipalType(currentToken);
        UUID principalId = jwtProvider.extractPrincipalId(currentToken);

        refreshTokenService.consume(type, principalId, currentToken);

        CustomUserDetails userDetails = (type == PrincipalType.ACCOUNT
                ? (CustomUserDetails) accountUserDetailsService.loadUserByUsername(principalId.toString())
                : CustomUserDetails.fromCustomer(customerRepository.findById(principalId)
                        .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED))));

        if (!userDetails.isEnabled()) {
            throw new BadRequestException(ErrorCode.ACCOUNT_DISABLED);
        }

        boolean requiresScope = type == PrincipalType.ACCOUNT && userDetails.getScopes().isEmpty();
        if (type == PrincipalType.ACCOUNT) {
            permissionService.saveSnapshot(principalId, permissionService.snapshotFromDetails(userDetails));
        }

        AuthResponse response = type == PrincipalType.ACCOUNT
                ? issueTokens(userDetails, requiresScope, null)
                : issueTokens(userDetails, false,
                    authMapper.toCustomerResponse(customerRepository.findById(principalId).orElseThrow()));
        return response;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7).trim() : accessToken.trim();
            if (jwtProvider.validateToken(token) && jwtProvider.isAccessToken(token)) {
                PrincipalType type = jwtProvider.extractPrincipalType(token);
                UUID principalId = jwtProvider.extractPrincipalId(token);
                if (type == PrincipalType.ACCOUNT) {
                    revokeAccountTokens(principalId);
                }
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()
                && jwtProvider.validateToken(refreshToken) && jwtProvider.isRefreshToken(refreshToken)) {
            PrincipalType type = jwtProvider.extractPrincipalType(refreshToken);
            UUID principalId = jwtProvider.extractPrincipalId(refreshToken);
            refreshTokenService.revoke(type, principalId, refreshToken);
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AuthResponse selectBranch(SelectBranchRequest request) {
        CustomUserDetails current = SecurityUtils.getCurrentUserDetails()
                .orElseThrow(() -> new BadRequestException(ErrorCode.UNAUTHENTICATED));
        if (current.getPrincipalType() != PrincipalType.ACCOUNT) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        UUID branchId = request.branchId();
        Account account = accountRepository.findById(current.getPrincipalId())
                .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));

        boolean allowed = branchId != null && (
                current.getScopes().stream().anyMatch(scope ->
                        scope.scopeType() == ScopeType.ALL_SYSTEM
                                || (scope.branchId() != null && scope.branchId().equals(branchId)))
                || (account.getPrimaryBranchId() != null && account.getPrimaryBranchId().equals(branchId))
        );

        if (!allowed) {
            throw new BadRequestException(ErrorCode.CROSS_SCOPE_DENIED);
        }

        CustomUserDetails withBranch = CustomUserDetails.withBranch(current, branchId);
        auditLogin(PrincipalType.ACCOUNT, account.getId(), AuditAction.LOGIN_SUCCESS,
                Map.of("action", "select_branch", "branchId", branchId.toString()));
        return issueTokens(withBranch, false, null);
    }

    /** Xác định branch tự động chọn nếu tài khoản chỉ có đúng một scope có branch cụ thể. */
    private UUID resolveAutoBranch(List<ScopeResponse> scopes) {
        if (scopes == null || scopes.size() != 1) {
            return null;
        }
        return scopes.get(0).branchId();
    }

    /** Xác thực theo loại thực thể: account qua AuthenticationManager, customer tự kiểm tra. */
    private CustomUserDetails authenticate(PrincipalType type, String identifier, String password) {
        if (type == PrincipalType.ACCOUNT) {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier.trim(), password));
            return (CustomUserDetails) authentication.getPrincipal();
        }
        Customer customer = customerRepository.findByUsernameOrPhoneOrEmail(identifier.trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (customer.getStatus() != EntityStatus.ACTIVE) {
            throw new BadCredentialsException("Customer disabled");
        }
        if (!customer.isHasLocalPassword() || !passwordEncoder.matches(password, customer.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return CustomUserDetails.fromCustomer(customer);
    }

    /** Cấp cặp access/refresh token, lưu refresh token (đa hình) và trả về response. */
    private AuthResponse issueTokens(CustomUserDetails userDetails, boolean requiresScopeAssignment,
                                     CustomerResponse customer) {
        String accessToken = jwtProvider.generateAccessToken(userDetails);
        String refreshToken = jwtProvider.generateRefreshToken(userDetails.getPrincipalType(), userDetails.getPrincipalId());
        refreshTokenService.issue(userDetails.getPrincipalType(), userDetails.getPrincipalId(), refreshToken, null, null);
        return authMapper.toResponse(accessToken, refreshToken, userDetails.getPrincipalType(),
                customer, requiresScopeAssignment, false, null);
    }

    /** Thu hồi toàn bộ token của tài khoản (access cũ + snapshot cache). */
    private void revokeAccountTokens(UUID accountId) {
        permissionService.evictSnapshot(accountId);
        accountRevocationService.revokeAccount(accountId, accessTokenLifetime);
    }

    /**
     * Xác định loại thực thể (ACCOUNT / CUSTOMER) cho đăng nhập. Nếu client truyền
     * {@code requested} khớp với thực thể tồn tại thì dùng nó; ngược lại tự phát hiện
     * theo định danh để chống trường hợp client gửi sai {@code type} (vd: khách hàng
     * gửi ACCOUNT). Ưu tiên ACCOUNT khi trùng định danh.
     */
    private PrincipalType resolvePrincipalType(PrincipalType requested, String identifier) {
        boolean accountExists = accountRepository.findByUsernameOrEmail(identifier, identifier).isPresent();
        boolean customerExists = customerRepository.findByUsernameOrPhoneOrEmail(identifier).isPresent();
        if (requested != null) {
            if (requested == PrincipalType.ACCOUNT && accountExists) {
                return PrincipalType.ACCOUNT;
            }
            if (requested == PrincipalType.CUSTOMER && customerExists) {
                return PrincipalType.CUSTOMER;
            }
        }
        if (accountExists) {
            return PrincipalType.ACCOUNT;
        }
        if (customerExists) {
            return PrincipalType.CUSTOMER;
        }
        return requested != null ? requested : PrincipalType.ACCOUNT;
    }

    /** Xác định id thực thể để ghi audit khi đăng nhập thất bại. */
    private UUID resolveActorId(PrincipalType type, String identifier) {
        if (type == PrincipalType.ACCOUNT) {
            return accountRepository.findByUsernameOrEmail(identifier, identifier).map(Account::getId).orElse(null);
        }
        return customerRepository.findByUsernameOrPhoneOrEmail(identifier).map(Customer::getId).orElse(null);
    }

    /** Sinh mã khách hàng duy nhất. */
    private String generateCustomerCode() {
        return "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /** Gửi email chào mừng cho khách hàng vừa đăng ký (nếu có email). */
    private void sendWelcomeEmail(Customer customer) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            return;
        }
        String fullName = customer.getFullName() != null ? customer.getFullName() : customer.getEmail();
        Map<String, Object> variables = Map.of(
                "fullName", fullName,
                "appName", brandName,
                "year", String.valueOf(java.time.Year.now().getValue())
        );
        try {
            mailService.sendTemplate(customer.getEmail(), "Chào mừng bạn đến với " + brandName, "mail/welcome", variables);
        } catch (Exception e) {
            log.error("Gửi email chào mừng thất bại (SMTP chưa cấu hình?): {}", customer.getEmail(), e);
        }
    }

    /** Ghi nhận một sự kiện đăng nhập (thành công/thất bại) vào audit log. */
    private void auditLogin(PrincipalType type, UUID actorId, AuditAction action, Map<String, Object> details) {
        auditService.record(new AuditEvent(
                type,
                actorId,
                action,
                AuditModule.SYS,
                type == PrincipalType.ACCOUNT ? AuditTargetType.ACCOUNT : AuditTargetType.CUSTOMER,
                actorId,
                details
        ));
    }
}
