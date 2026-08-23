# Luồng đăng nhập & xác thực (Authentication Flow)

Tài liệu dành cho **Frontend** (và dev nội bộ) để nối BE một cách chuẩn.
Base path chung của auth: **`/api/v1/auth`**

> ⚠️ **Thay đổi gần nhất (quan trọng):** response đăng nhập **đã bỏ trường `account`**.
> Thông tin tài khoản KHÔNG còn nằm trong kết quả login. FE lấy profile qua token
> (claim `sub` = principalId) + gọi API tương ứng (xem mục 7).

---

## 1. Tổng quan

- Xác thực dùng **JWT**: `accessToken` (ngắn hạn) + `refreshToken` (dài hạn).
- Gửi access token qua header: `Authorization: Bearer <accessToken>`.
- Hai loại thực thể (`principalType`):
  - **ACCOUNT** — tài khoản nội bộ (nhân viên / admin / quản lý).
  - **CUSTOMER** — khách hàng.
- Tất cả response bọc trong `ApiResponse<T>`:
  ```json
  { "code": 200, "message": "OK", "data": { ... }, "timestamp": "..." }
  ```

---

## 2. Access Token chứa gì (JWT claims)

Giải mã phần payload (base64url) của `accessToken` sẽ thấy:

| Claim | Ý nghĩa |
|-------|---------|
| `sub` | **principalId** (UUID) — id của account hoặc customer |
| `principalType` | `ACCOUNT` \| `CUSTOMER` |
| `username` | username (account) hoặc email/phone (customer) |
| `roleCodes` | danh sách vai trò, dạng `["ROLE_ADMIN", ...]` |
| `scopes` | danh sách phạm vi, dạng `["<scopeId>|<SCOPE_TYPE>|<branchId>"]` |
| `tokenType` | `"access"` |
| `branchId` | **(tùy chọn)** chỉ có khi user đang làm việc tại 1 chi nhánh cụ thể |
| `jti` | id của token |
| `iat` / `exp` | thời điểm phát hành / hết hạn |

Refresh token chỉ chứa `sub`, `principalType`, `tokenType=refresh`, `jti`, `iat`, `exp`.

---

## 3. Các bước đăng nhập (ACCOUNT)

### Bước 1 — Login
`POST /api/v1/auth/login`

```json
{
  "usernameOrEmail": "admin",
  "password": "123456789",
  "type": "ACCOUNT"
}
```
- `type` optional; nếu không truyền BE tự suy ra (ACCOUNT nếu là username/email nội bộ, CUSTOMER nếu là phone/email khách).

**Response (đã bỏ `account`):**
```json
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "principalType": "ACCOUNT",
    "customer": null,
    "requiresScopeAssignment": false,
    "requiresEmailVerification": false,
    "verifyToken": null
  }
}
```

### Bước 2 — (Tùy chọn) Chọn chi nhánh `select-branch`
> 👉 **Lưu ý về branch:** tại bước login, access token **thường CHƯA có `branchId`**
> (trừ khi scope của tài khoản đã gắn sẵn chi nhánh → BE tự gán "auto-branch").
> Với tài khoản hệ thống (ALL_SYSTEM, ví dụ admin) hoặc chưa gán scope, `branchId` = null.
> Để làm việc tại 1 chi nhánh cụ thể, FE gọi:

`POST /api/v1/auth/select-branch`
```json
{ "branchId": "00000000-0000-0000-0000-000000000001" }
```
→ Trả về `AuthResponse` mới, **access token lúc này có claim `branchId`**.
Sau bước này, FE dùng token mới cho các request tiếp theo.

Nếu `requiresScopeAssignment = true` → tài khoản chưa có scope, FE bắt buộc gọi
`select-branch` (hoặc màn hình chọn đơn vị) trước khi vào nghiệp vụ.

---

## 4. Khách hàng (CUSTOMER)

| Mục đích | Endpoint | Ghi chú |
|----------|----------|---------|
| Đăng ký | `POST /api/v1/auth/register` | Nếu email chưa xác thực → response có `requiresEmailVerification=true` + `verifyToken` |
| Xác thực OTP email | `POST /api/v1/auth/verify-email` | dùng `verifyToken` + otp |
| Gửi lại OTP | `POST /api/v1/auth/resend-otp` | |
| Quên mật khẩu | `POST /api/v1/auth/forgot-password` | trả về `resetToken` |
| Đặt lại mật khẩu | `POST /api/v1/auth/reset-password` | dùng `resetToken` + otp + newPassword |
| Đổi mật khẩu | `POST /api/v1/auth/change-password` | cần mật khẩu cũ, áp dụng cho user đang login |
| Google OAuth2 | `POST /api/v1/auth/oauth2/google` | gửi Google ID token |

Response các trường hợp trên đều là `AuthResponse` (có `principalType=CUSTOMER`,
trường `customer` chứa tóm tắt thông tin khách, và `account` = null).

---

## 5. Làm mới & đăng xuất

### Refresh token
`POST /api/v1/auth/refresh-token`
```json
{ "refreshToken": "eyJhbGci..." }
```
→ trả về cặp token mới (`accessToken` + `refreshToken`).

### Logout
`POST /api/v1/auth/logout`
- Header: `Authorization: Bearer <accessToken>`
- Body: `{ "refreshToken": "..." }`
→ thu hồi cả access và refresh token.

---

## 6. Ví dụ decode access token (sau select-branch)

```json
{
  "sub": "c0000000-0000-0000-0000-000000000001",
  "principalType": "ACCOUNT",
  "username": "admin",
  "roleCodes": ["ROLE_ADMIN"],
  "scopes": ["<scopeId>|ALL_SYSTEM|"],
  "tokenType": "access",
  "branchId": "00000000-0000-0000-0000-000000000001",
  "jti": "uuid",
  "iat": 169...,
  "exp": 169...
}
```

---

## 7. FE lấy thông tin người dùng (vì response login KHÔNG còn `account`)

1. Decode `accessToken` (base64url payload) → lấy `sub` (principalId) và `principalType`.
2. Gọi API profile tương ứng:
   - **ACCOUNT**: `GET /api/v1/accounts/{sub}`
   - **CUSTOMER**: gọi API profile khách hàng tương ứng (dùng `{sub}`).
3. Không dùng lại `response.account` từ login (trường này đã bị xóa).

> Nếu sau này BE bổ sung endpoint `GET /api/v1/auth/me`, FE có thể gọi trực tiếp
> thay vì tự ghép `{sub}` — sẽ cập nhật ở đây.

---

## 8. Lưu ý tích hợp cho FE

- Luôn gửi `Authorization: Bearer <accessToken>` cho mọi request cần xác thực.
- Đọc `branchId` từ claim của access token; nếu `null` → gọi `/select-branch`.
- Khi access token hết hạn → gọi `/refresh-token` bằng refreshToken để lấy cặp mới
  (không bắt user login lại).
- `principalType` quyết định quyền/hành động: ACCOUNT có role nội bộ, CUSTOMER là khách.
- Mật khẩu trong DB được băm (BCrypt) — BE không bao giờ trả về mật khẩu.

---

## 9. Danh sách endpoint nhanh

| Method | Path | Mô tả |
|--------|------|-------|
| POST | `/api/v1/auth/register` | Đăng ký khách hàng |
| POST | `/api/v1/auth/login` | Đăng nhập |
| POST | `/api/v1/auth/verify-email` | Xác thực OTP email |
| POST | `/api/v1/auth/resend-otp` | Gửi lại OTP email |
| POST | `/api/v1/auth/forgot-password` | Quên mật khẩu |
| POST | `/api/v1/auth/reset-password` | Đặt lại mật khẩu |
| POST | `/api/v1/auth/change-password` | Đổi mật khẩu |
| POST | `/api/v1/auth/oauth2/google` | Login Google |
| POST | `/api/v1/auth/refresh-token` | Làm mới token |
| POST | `/api/v1/auth/select-branch` | Chọn chi nhánh (gán branchId vào token) |
| POST | `/api/v1/auth/logout` | Đăng xuất |
