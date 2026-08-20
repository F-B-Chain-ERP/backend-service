# Sprint 1 — Kế hoạch triển khai Auth & Phân quyền (RBAC + Data Scope)

> Phạm vi: Hoàn thiện toàn bộ chức năng xác thực (Login S1-01) và nền tảng phân quyền
> RBAC + Data Scope theo tài liệu đặc tả S1-01 / S1-03.
> **Quy ước**: Giữ nguyên tên bảng hiện tại (`account`, `role`, `permission`,
> `role_permission`, `account_role_assignment`, `audit_log`); bổ sung bảng `scope`.

---

## 0. Hiện trạng & Gap (đã xác thực từ code)

| Hạng mục | Hiện tại | Cần đạt |
|---|---|---|
| `scope` | Chỉ có hằng `TableName.SCOPE`, chưa có table/entity/repo | Thêm bảng `scope(type, branch_id, status)` |
| `account_role_assignment` | PK composite `(account_id, role_id)` (`AccountRole`) | Đổi sang surrogate `id`, thêm `scope_id`, unique `(account_id, role_id, scope_id)` |
| Lấy quyền lúc login | `CustomUserDetailsService` query role ACTIVE, **không** lọc `expires_at`, **không** lọc scope | Lấy assignment hợp lệ (status + expiry + role active + scope active) → build roles/permissions/**scopes** |
| JWT | claim `roles`, `permissions` | thêm claim `scopes` |
| `AuthResponse` | chỉ token | thêm `account`, `roles`, `permissions`, `scopes`, `requiresActivation` |
| Audit login | chưa có ghi `audit_log` | ghi `LOGIN_SUCCESS` / `LOGIN_FAILED` / `ACCESS_DENIED` |
| BR-LG-05 (khóa 15p sau 5 lần sai) | `RateLimitFilter` chỉ giới hạn global | Redis counter khóa theo account |
| Layer 1 / Layer 2 (API authz) | `@EnableMethodSecurity` bật nhưng chưa dùng `@PreAuthorize`, chưa có Scope check | Interceptor/Service kiểm tra permission + scope |
| Token revocation | `accountRevocationService.isRevoked` đã có tại `JwtAuthFilterChain` | tái dùng khi account INACTIVE / thu hồi assignment |

---

## 1. Kiến trúc phân quyền mục tiêu

- Mô hình **RBAC + Data Scope** (S1-03).
- Quyền thực tế của Account = **union** của tất cả `account_role_assignment` đang **có hiệu lực**
  (status = ACTIVE, `expires_at` null hoặc > now, role ACTIVE, scope ACTIVE) → BR-AUTH-03.
- Kiểm soát 2 lớp tại Backend (UC-02, SEC-AUTH-01):
  - **Layer 1 – Action Permission**: tài khoản ACTIVE + có permission tương ứng.
  - **Layer 2 – Data Scope**: request có `branchId` → phải khớp `ALL_SYSTEM` hoặc
    `STORE`/`WAREHOUSE` cùng `branchId` → BR-AUTH-02.
- Scope **không** tự động suy ra từ Role; luôn đi theo từng Assignment.

### Loại Scope (bảng `scope`)
| scope_type | branch_id | Ý nghĩa |
|---|---|---|
| `ALL_SYSTEM` | NULL | Toàn bộ dữ liệu chuỗi |
| `STORE` | UUID | Giới hạn chi nhánh cửa hàng cụ thể |
| `WAREHOUSE` | UUID | Giới hạn kho tổng cụ thể |

---

## 2. Plan triển khai theo Phase

### Phase 0 — DB Migration & Entity (blocker)
1. **Changeset mới (Liquibase)** tạo bảng `scope`:
   - `id uuid pk`, `scope_type varchar(30) NOT NULL`, `branch_id uuid`,
     `status varchar(30) default 'ACTIVE'`, audit fields,
     unique `(scope_type, branch_id)`.
2. **Alter `account_role_assignment`**:
   - thêm `id uuid pk` (sinh từ `(account_id, role_id)` cho data cũ),
   - thêm `scope_id uuid NOT NULL` + FK `scope(id)`,
   - unique index `(account_id, role_id, scope_id)`,
   - thêm `assigned_by`, `updated_at`, `updated_by`.
   - Data cũ: gán `scope_id` = ALL_SYSTEM mặc định (branch_id NULL).
3. Entity/repo mới (`core-model`): `Scope`, `AccountRoleAssignment` (thay `AccountRole`),
   `ScopeRepository`, `AccountRoleAssignmentRepository`, `AuditLogRepository`.

### Phase 1 — Domain & Permission Matrix (seed data)
4. Seed `scope` ALL_SYSTEM mặc định (id cố định).
5. Mở rộng `002-init-seed-data.sql`:
   - Roles: `STORE_MANAGER, STAFF, INVENTORY_MANAGER, PRODUCT_MANAGER, ACCOUNTANT`
     (giữ `ADMIN, USER`).
   - 06 module permissions (SYS/POS/STORE/INV/MENU/FIN) theo ma trận S1-03.
   - `role_permission` theo Role-Permission Matrix (mục 4 đặc tả).

### Phase 2 — Login & JWT (S1-01 + scope)
6. `CustomUserDetailsService.loadUserByUsername`:
   - lấy assignment hợp lệ (status + expiry + role active + scope active),
   - build `roles`, `permissions`, `scopes`.
   - Assignment rỗng + account ACTIVE → vẫn login, scopes rỗng → `requiresActivation = true`.
7. `CustomUserDetails`: thêm `List<ScopeInfo> scopes`.
8. `JwtProvider.generateAccessToken`: thêm claim `scopes`.
9. `AuthResponse`: thêm `account`, `roles`, `permissions`, `scopes`, `requiresActivation`.
10. `AuthServiceImpl.login`: ghi `audit_log` `LOGIN_SUCCESS` (ip, user_agent,
    **không** log password); sai password → `LOGIN_FAILED`.

### Phase 3 — Account Lock (BR-LG-05)
11. `LoginFailureService` (Redis): `fail:<username>` counter; đạt 5 →
    `lock:<username>` 15 phút. Login check lock trước authenticate → `ACCOUNT_LOCKED`.

### Phase 4 — Runtime Authorization (UC-02)
12. `AuthorizationService.checkPermission(accountId, permissionCode)` (Layer 1).
13. `AuthorizationService.checkScope(accountId, branchId)` (Layer 2):
    union scope → ALL_SYSTEM hợp lệ OR (STORE/WAREHOUSE + branchId khớp).
14. AOP / `@PreAuthorize` helper `@RequiresPermission` + `@RequiresScope`
    dùng `SecurityUtils` + `AuthorizationService`.
15. Fail → `PERMISSION_DENIED` / `CROSS_SCOPE_DENIED` (403) + audit `ACCESS_DENIED_SUSPICIOUS`.

### Phase 5 — Quản trị phân quyền (UC-01)
16. `RoleAssignmentService.assign(accountId, roleId, scopeId, expiresAt)`:
    - validate account ACTIVE, role EXISTS, scope EXISTS (error code mục 9.2),
    - trùng active → 409 `ASSIGNMENT_EXISTS`,
    - BR-AUTH-05: chặn thu hồi/khóa ADMIN gốc → `CANNOT_MODIFY_ADMIN`,
    - 1 transaction: lưu `account_role_assignment` + audit `ASSIGN_ROLE` (BR-AUTH-07).
17. `revoke` tương tự → audit `REVOKE_ROLE`.

### Phase 6 — Token Revocation (SEC-AUTH-03)
18. Tái dùng `accountRevocationService`: account → INACTIVE hoặc thu hồi toàn bộ
    assignment → đánh dấu revoke token để `JwtAuthFilterChain` từ chối.

### Phase 7 — ErrorCode & Messages
19. Bổ sung `ErrorCode`: `ASSIGNMENT_REQUIRED_FIELDS(400)`, `ASSIGNMENT_EXISTS(409)`,
    `ACCOUNT_NOT_FOUND(404)`, `ACCOUNT_INACTIVE(401)`, `ROLE_NOT_FOUND(404)`,
    `SCOPE_NOT_FOUND(404)`, `PERMISSION_DENIED(403)`, `CROSS_SCOPE_DENIED(403)`,
    `CANNOT_MODIFY_ADMIN(400)`, `ACCOUNT_SESSION_REVOKED(401)`, `ACCOUNT_LOCKED(401)`,
    `LOGIN_FAILED`.
20. Map UI messages (mục 9.1 đặc tả).

### Phase 8 — Frontend
21. Login response: nếu `requiresActivation` → route màn hình chờ kích hoạt (ẩn sidebar).
    Ngược lại hiển thị menu theo `permissions`.
22. Xử lý 403 → message tương ứng (không có quyền / sai scope / hết hạn).

---

## 3. Thứ tự ưu tiên

1. **Phase 0 + 1** (DB/scope/seed) — blocker.
2. **Phase 2** (login + JWT + response) — đóng S1-01 + scope cơ bản.
3. **Phase 3** (lock) + **Phase 7** (error codes).
4. **Phase 4** (Layer 1/2 authz) — cốt lõi bảo mật.
5. **Phase 5** (admin assign) + **Phase 6** (revocation).
6. **Phase 8** (FE).

---

## 4. Rủi ro & Lưu ý

- Migration đổi PK `account_role_assignment` → cần script chuyển data cẩn thận
  (sinh `id`, gán `scope_id` ALL_SYSTEM).
- JWT cũ chưa có `scopes` → phiên cũ dùng được đến hết hạn (chấp nhận).
- Hiệu năng Layer 2: mỗi request query assignment → nên cache permission/scope
  theo `accountId` (Redis) thay vì query DB mỗi lần.
- Giữ `refresh_token` lưu Redis (chưa dựng bảng `ia_refresh_token` theo DB dự kiến).

---

## 5. Ma trận quyền tham chiếu (tóm tắt)

06 module: `SYS, POS, STORE, INV, MENU, FIN`. Chi tiết theo Role-Permission Matrix
S1-03 mục 4. Quy tắc: ADMIN full; INVENTORY_MANAGER mặc định WAREHOUSE cụ thể,
ALL_SYSTEM chỉ khi được cấp bằng Assignment cụ thể.
