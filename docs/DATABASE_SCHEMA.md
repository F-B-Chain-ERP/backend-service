# Database Schema — ERP Backend

Tài liệu mô tả toàn bộ bảng hiện có trong hệ thống (module `core-model`, JPA entities).
Cập nhật dựa trên source code hiện tại.

> Quy ước kiểu dữ liệu: `uuid` = UUID, `varchar(n)` = string, `bool` = boolean,
> `timestamp` = Instant, `numeric(p,s)` = BigDecimal, `jsonb` = JSON.
> Mọi bảng (trừ `role_permission`) đều kế thừa `BaseAuditingEntity` sinh thêm các cột chung:
> - `id` uuid PK (tự sinh bằng UUID.randomUUID())
> - `created_at` timestamp NOT NULL
> - `updated_at` timestamp NOT NULL
> - `created_by` varchar(100)
> - `updated_by` varchar(100)

---

## 1. account
Tài khoản nội bộ (do admin cấp, không tự đăng ký).

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| username | varchar(100) | NOT NULL | | Định danh đăng nhập |
| password | varchar(255) | NULL | | Băm bcrypt |
| full_name | varchar(150) | NOT NULL | | |
| email | varchar(150) | NULL | | |
| phone | varchar(20) | NULL | | |
| avatar_url | varchar(500) | NULL | | |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |
| last_login_at | timestamp | NULL | | |
| auth_provider | varchar(50) | NOT NULL | `LOCAL` | AuthProvider |
| provider_id | varchar(255) | NULL | | sub của OAuth2 |
| system_protected | bool | NOT NULL | false | không cho xóa/sửa |
| primary_branch_id | uuid | NULL | | FK -> branch.id |
| has_local_password | bool | NOT NULL | true | |
| created_at / updated_at / created_by / updated_by | | | | từ BaseAuditingEntity |

---

## 2. role
Vai trò của tài khoản nội bộ.

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| code | varchar(80) | NOT NULL | | Mã vai trò (duy nhất logic) |
| name | varchar(150) | NOT NULL | | Tên hiển thị |
| description | varchar(255) | NULL | | |
| type | varchar(30) | NOT NULL | `SYSTEM` | RoleType: SYSTEM/TENANT/LOCAL |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |

---

## 3. permission
Quyền hạn chi tiết.

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| code | varchar(120) | NOT NULL | | Mã quyền |
| name | varchar(150) | NOT NULL | | |
| module | varchar(80) | NOT NULL | | Phân nhóm module |
| description | varchar(255) | NULL | | |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |

---

## 4. role_permission
Bảng liên kết nhiều-nhiều Role <-> Permission (không có bảng auditing).

| Cột | Kiểu | Null | Ghi chú |
|---|---|---|---|
| role_id | uuid | NOT NULL | PK (part 1), FK -> role.id |
| permission_id | uuid | NOT NULL | PK (part 2), FK -> permission.id |

> PK composite (`@IdClass` RolePermissionId). Không có cột `id/created_at/...`.

---

## 5. scope
Phạm vi (data boundary) áp dụng cho vai trò.

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| scope_type | varchar(30) | NOT NULL | | ScopeType: ALL_SYSTEM/STORE/WAREHOUSE |
| branch_id | uuid | NULL | | FK -> branch.id |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |

---

## 6. account_role_assignment
Gán vai trò cho account trong một scope cụ thể.

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| account_id | uuid | NOT NULL | | FK -> account.id |
| role_id | uuid | NOT NULL | | FK -> role.id |
| scope_id | uuid | NOT NULL | | FK -> scope.id |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |
| assigned_at | timestamp | NOT NULL | now() | |
| assigned_by | varchar(100) | NULL | | |
| expires_at | timestamp | NULL | | hết hạn gán |

---

## 7. branch
Chi nhánh / đơn vị kinh doanh (dùng làm scope và chọn đơn vị sau đăng nhập).

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| code | varchar(50) | NOT NULL | | **UNIQUE** |
| name | varchar(150) | NOT NULL | | |
| address | varchar(255) | NULL | | |
| phone | varchar(20) | NULL | | |
| email | varchar(150) | NULL | | |
| latitude | numeric(10,7) | NULL | | |
| longitude | numeric(10,7) | NULL | | |
| timezone | varchar(50) | NOT NULL | `Asia/Ho_Chi_Minh` | |
| supports_pickup | bool | NOT NULL | true | |
| supports_delivery | bool | NOT NULL | false | |
| average_preparation_minutes | int | NOT NULL | 15 | |
| status | varchar(30) | NOT NULL | `ACTIVE` | (chuỗi, không enum) |
| parent_id | uuid | NULL | | FK tự tham chiếu -> branch.id |

---

## 8. customer
Khách hàng (tự đăng ký qua `/api/v1/auth/register`). Hoàn toàn tách biệt với `account`.

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| customer_code | varchar(50) | NOT NULL | tự sinh | **KHÔNG có unique constraint** ⚠️ |
| full_name | varchar(150) | NOT NULL | | |
| phone | varchar(20) | NULL | | |
| email | varchar(150) | NULL | | |
| password | varchar(255) | NULL | | Băm bcrypt |
| auth_provider | varchar(30) | NOT NULL | `LOCAL` | AuthProvider |
| provider_id | varchar(150) | NULL | | sub OAuth2 |
| has_local_password | bool | NOT NULL | true | |
| email_verified | bool | NOT NULL | false | |
| avatar_url | varchar(500) | NULL | | |
| date_of_birth | date | NULL | | |
| gender | varchar(20) | NULL | | |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |
| last_login_at | timestamp | NULL | | |

> Đăng ký: bắt buộc `full_name` + `password`. Không có `username`.
> Định danh đăng nhập là `phone` hoặc `email` (xem `findByPhoneOrEmail`).
> Trùng lặp `phone`/`email` chỉ check ở app-level (`existsByPhone`/`existsByEmail`),
> DB **không** có unique constraint → có rủi ro race condition.

---

## 9. customer_address
Địa chỉ giao hàng của khách hàng (1 customer : nhiều address).

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| customer_id | uuid | NOT NULL | | FK -> customer.id |
| receiver_name | varchar(150) | NOT NULL | | |
| receiver_phone | varchar(20) | NOT NULL | | |
| address_line | varchar(255) | NOT NULL | | |
| ward | varchar(100) | NULL | | |
| district | varchar(100) | NULL | | |
| city | varchar(100) | NULL | | |
| latitude | numeric(10,7) | NULL | | |
| longitude | numeric(10,7) | NULL | | |
| is_default | bool | NOT NULL | false | tối đa 1 mặc định |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |

---

## 10. refresh_token
Lưu refresh token trên DB (đa hình: account hoặc customer).

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |
| principal_type | varchar(20) | NOT NULL | | PrincipalType: ACCOUNT/CUSTOMER |
| principal_id | uuid | NOT NULL | | FK đa hình -> account.id hoặc customer.id |
| token_hash | varchar(255) | NOT NULL | | **UNIQUE** (băm, không lưu plaintext) |
| device_info | varchar(255) | NULL | | |
| ip_address | varchar(64) | NULL | | |
| expires_at | timestamp | NOT NULL | | |
| revoked_at | timestamp | NULL | | |

> Index: `idx_refresh_principal(principal_type, principal_id, expires_at)`,
> `idx_refresh_token_hash(token_hash)` unique.

---

## 11. audit_log
Lịch sử hệ thống, actor đa hình (account hoặc customer).

| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | uuid | NOT NULL | tự sinh | PK |
| status | varchar(30) | NOT NULL | `ACTIVE` | EntityStatus |
| actor_type | varchar(20) | NULL | | PrincipalType: ACCOUNT/CUSTOMER |
| actor_id | uuid | NULL | | FK đa hình |
| action | varchar(100) | NOT NULL | | ví dụ LOGIN_SUCCESS |
| module | varchar(80) | NOT NULL | | |
| target_type | varchar(80) | NULL | | |
| target_id | uuid | NULL | | |
| branch_id | uuid | NULL | | FK -> branch.id |
| ip_address | varchar(64) | NULL | | |
| user_agent | varchar(500) | NULL | | |
| before_data | jsonb | NULL | | Map<String,Object> |
| after_data | jsonb | NULL | | Map<String,Object> |

---

## Mối quan hệ (FK logic)

```
account.role          account.id ───< account_role_assignment.account_id
role.id               role.id    ───< account_role_assignment.role_id
scope.id              scope.id   ───< account_role_assignment.scope_id
role.id               role.id    ───< role_permission.role_id  (PK)
permission.id         permission.id ─< role_permission.permission_id (PK)
branch.id             branch.id  ───< account.primary_branch_id
                      branch.id  ───< scope.branch_id
                      branch.id  ───< audit_log.branch_id
                      branch.id  ───< branch.parent_id (tự tham chiếu)
customer.id           customer.id ──< customer_address.customer_id
customer.id/account.id (principal) ──< refresh_token.principal_id
customer.id/account.id (actor/target) ──< audit_log.actor_id / target_id
```

## Enum tham chiếu
- **EntityStatus**: ACTIVE, INACTIVE, DELETED, LOCKED
- **AuthProvider**: LOCAL, GOOGLE, MICROSOFT, GITHUB, LDAP, SAML
- **PrincipalType**: ACCOUNT, CUSTOMER
- **RoleType**: SYSTEM, TENANT, LOCAL
- **ScopeType**: ALL_SYSTEM, STORE, WAREHOUSE

## Lưu ý rủi ro (hiện tại)
1. `customer.customer_code` sinh tự động nhưng **không có unique constraint** và không check trùng → nguy cơ trùng mã.
2. `customer.phone` / `customer.email` **không có unique constraint** ở DB, chỉ check app-level → race condition khi 2 request cùng lúc.
3. `account.username`, `role.code`, `permission.code`, `branch.code` (branch có unique) — account/role/permission chưa thấy unique constraint rõ ràng.
