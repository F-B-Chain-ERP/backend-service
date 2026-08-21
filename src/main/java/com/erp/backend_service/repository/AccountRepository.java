package com.erp.backend_service.repository;

import com.erp.core.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu tài khoản (Account). */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Tìm tài khoản theo tên đăng nhập. */
    Optional<Account> findByUsername(String username);

    /** Tìm tài khoản theo email. */
    Optional<Account> findByEmail(String email);

    /** Tìm tài khoản theo username hoặc email (dùng cho đăng nhập). */
    Optional<Account> findByUsernameOrEmail(String username, String email);
}

