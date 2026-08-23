# ⚙️ HƯỚNG DẪN & QUY CHUẨN PHÁT TRIỂN REPOSITORY `backend-service`
## DÀNH CHO DEV PHÁT TRIỂN BACKEND REST API (SPRING BOOT 3)

---

## 🌿 1. QUY ĐỊNH GIT FLOW & NHÁNH LÀM VIỆC (BRANCHING RULES)

### 1.1. Cú pháp đặt tên nhánh (Checkout từ nhánh `dev`)
Tất cả các nhánh làm việc bắt buộc phải được **checkout từ nhánh `dev`**:

* **Task tính năng mới (Feature):**
  $$\mathbf{feature/\{\text{tên\_dev}\}/\{\text{mã\_task}\}}$$
  *Ví dụ:* `feature/nguyen_toan/S2-09`, `feature/tung/S2-10`, `feature/hoan/S2-11`
* **Task sửa lỗi (Fix Bug):**
  $$\mathbf{fixbug/\{\text{tên\_dev}\}/\{\text{mã\_task}\}} \quad \text{hoặc} \quad \mathbf{fixbug/\{\text{tên\_dev}\}/\{\text{mã\_bug}\}}$$
  *Ví dụ:* `fixbug/nguyen_toan/S2-09`, `fixbug/tung/S2-BUG-03`
* **Tối ưu / Refactor:**
  $$\mathbf{refactor/\{\text{tên\_dev}\}/\{\text{mã\_task}\}}$$

---

### 1.2. Quy định Nhánh đích khi tạo Pull Request (Target Branch)
> [!IMPORTANT]
> **Nhánh đích khi tạo PR:** 👉 **`dev-2`** *(Merge vào nhánh làm việc tập trung của Sprint 2)*  
> **Quy trình:** Sau khi toàn bộ tính năng và test cases của Sprint 2 hoàn thành, Tester nghiệm thu pass 100%, Tech Lead sẽ thực hiện merge nhánh `dev-2` $\rightarrow$ `dev`.

---

### 1.3. Quy tắc Commit Message (Conventional Commits)
- **Cú pháp:** `feat(mã_task): mô tả` hoặc `fix(mã_task): mô tả`
- *Ví dụ:*
  - `feat(S2-09): implement SupplierService and REST API endpoints`
  - `feat(S2-10): implement SupplierMaterial pricing and lead time APIs`
  - `feat(S2-11): implement PurchaseOrder workflow with state machine`
  - `fix(S2-11): prevent approving PO when status is not SUBMITTED`
  - `docs(S2-09): add JavaDocs for SupplierController and service methods`

---

## 🛠 2. QUY CHUẨN CODE BACKEND (SPRING BOOT 3 STANDARDS)

---

### 2.1. Bắt buộc viết JavaDocs đầy đủ và chuẩn mực
Mọi Class, Interface, Method trong **Controller, Service, Repository, Mapper** đều phải có JavaDocs rõ ràng:
- Mô tả tổng quan chức năng.
- Giải thích các tham số `@param`.
- Ý nghĩa dữ liệu trả về `@return`.
- Các lỗi nghiệp vụ có thể ném ra `@throws`.

**Ví dụ chuẩn JavaDocs:**
```java
/**
 * REST Controller quản lý nghiệp vụ Đơn đặt mua hàng (Purchase Order).
 * Cung cấp các đầu API tạo mới, cập nhật, trình duyệt, phê duyệt và hủy đơn.
 *
 * @author Nguyễn Toàn (Dev) / Hoàn (Reviewer)
 * @since Sprint 2 (2026)
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    /**
     * Lấy danh sách đơn đặt mua hàng có phân trang và bộ lọc linh hoạt.
     *
     * @param request DTO chứa các điều kiện lọc: mã PO, NCC, kho, trạng thái, khoảng ngày.
     * @return {@link ApiResponse} chứa {@link PageResponse} danh sách PO tóm tắt.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PROC_PO_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderResponse>>> getAll(
            @Valid PurchaseOrderFilterRequest request) {
        PageResponse<PurchaseOrderResponse> response = purchaseOrderService.getAll(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Phê duyệt đơn đặt mua hàng. Chỉ đơn ở trạng thái SUBMITTED mới được duyệt.
     *
     * @param id Mã định danh UUID của đơn mua hàng.
     * @return {@link ApiResponse} chứa thông tin chi tiết đơn hàng sau khi duyệt.
     * @throws BaseException mã PROC_404_PO_NOT_FOUND nếu không tìm thấy đơn.
     * @throws BaseException mã PROC_400_PO_INVALID_STATUS nếu trạng thái hiện tại khác SUBMITTED.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PROC_PO_APPROVE')")
    public ResponseEntity<ApiResponse<PurchaseOrderDetailResponse>> approve(
            @PathVariable UUID id) {
        PurchaseOrderDetailResponse response = purchaseOrderService.approve(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

---

### 2.2. Chuẩn hóa Thiết kế RESTful API
1. **URI Conventions:**
   - Dùng danh từ số nhiều, chữ thường, nối bằng dấu gạch ngang `-`.
   - Tuyệt đối không đưa động từ vào URI (Trừ các hành động đổi trạng thái nghiệp vụ).
   - *Chuẩn:*
     - `GET /api/v1/suppliers` (Danh sách phân trang)
     - `GET /api/v1/suppliers/{id}` (Chi tiết theo UUID)
     - `POST /api/v1/suppliers` (Tạo mới)
     - `PUT /api/v1/suppliers/{id}` (Cập nhật thông tin)
     - `DELETE /api/v1/suppliers/{id}` (Xóa mềm / vô hiệu hóa)
     - `POST /api/v1/purchase-orders/{id}/submit` (Trình duyệt)
     - `POST /api/v1/purchase-orders/{id}/approve` (Phê duyệt)
     - `POST /api/v1/purchase-orders/{id}/reject` (Từ chối)
     - `POST /api/v1/purchase-orders/{id}/cancel` (Hủy đơn)
2. **Wrapper phản hồi (Response Envelope):**
   - 100% API trả về bọc qua `ApiResponse<T>`:
     - Thành công: `ApiResponse.success(data)`
     - Phân trang: `ApiResponse.success(PageResponse<T>)`
     - Thao tác void: `ApiResponse.success(null)`
3. **Validation đầu vào:**
   - Luôn đặt `@Valid` trước `@RequestBody` hoặc query params DTO.
   - Không xử lý if-else validate cơ bản thủ công trong Service nếu đã có thể validate bằng Jakarta annotations.

---

### 2.3. Clean Code & Kiến trúc phân tầng (Layered Architecture)
> [!WARNING]
> **Quy định quan trọng về Thư viện & Boilerplate:**
> - ❌ **KHÔNG SỬ DỤNG LOMBOK:** Tuyệt đối không dùng `@Getter`, `@Setter`, `@Data`, `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Builder`. Bắt buộc tự viết tay Getters, Setters, Constructors tường minh.
> - ❌ **KHÔNG SỬ DỤNG MAPSTRUCT:** Không dùng annotation `@Mapper` hay code generation của MapStruct. Bắt buộc viết các class Mapper thủ công bằng tay gắn `@Component`.

- **Controller Layer:** Tiếp nhận HTTP Request $\rightarrow$ Kiểm tra `@Valid` & `@PreAuthorize` $\rightarrow$ Gọi Service $\rightarrow$ Trả về `ResponseEntity<ApiResponse<T>>`. Tuyệt đối không chứa logic nghiệp vụ, không query database trực tiếp.
- **Service Layer:**
  - Định nghĩa Interface `XxxService` và Class triển khai `XxxServiceImpl`.
  - Quản lý giao dịch dữ liệu: `@Transactional(readOnly = true)` ở mức class và `@Transactional` ở mức method thực hiện ghi/sửa/xóa.
  - **Dependency Injection:** Tự viết Constructor tường minh bằng tay (Manual Constructor Injection), không dùng `@Autowired` field.
  - Kiểm soát State Machine chặt chẽ (ví dụ: PO chỉ sửa khi `DRAFT`, chỉ duyệt khi `SUBMITTED`).
- **Xử lý Mối quan hệ qua ID (Flat ID Mapping):**
  - Do Entity trong `core-model` không dùng `@OneToMany` / `@ManyToOne`, tầng Service / Repository sẽ tự truy vấn và ghép nối dữ liệu theo ID:
    - Tìm danh sách con: `purchaseOrderItemRepository.findByPurchaseOrderId(poId)`.
    - Lấy thông tin đối tượng liên kết: `supplierRepository.findById(po.getSupplierId())`.
    - Gọi Mapper chuyển đổi sang `PurchaseOrderDetailResponse`.
- **Repository Layer:**
  - Kế thừa `JpaRepository<Entity, UUID>` và `JpaSpecificationExecutor<Entity>` để hỗ trợ tìm kiếm động linh hoạt.
- **Mapper Layer (Mapper thủ công bằng tay):**
  - Khai báo class `@Component` (ví dụ: `SupplierMapper`, `SupplierMaterialMapper`, `PurchaseOrderMapper`).
  - Viết các method chuyển đổi rõ ràng bằng tay (`toResponse(Entity entity)`, `toEntity(Request request)`, `toDetailResponse(...)`).
  - **Mẫu tham chiếu chuẩn:** Xem [`AccountMapper.java`](file:///c:/ERP-UTT/backend-service/src/main/java/com/erp/backend_service/mapper/AccountMapper.java) và [`RoleAssignmentMapper.java`](file:///c:/ERP-UTT/backend-service/src/main/java/com/erp/backend_service/mapper/RoleAssignmentMapper.java).
- **Exception & ErrorCode:**
  - Khai báo mã lỗi tập trung trong `com.erp.backend_service.exception.ErrorCode`.
  - Ném ngoại lệ nghiệp vụ qua `throw new BaseException(ErrorCode.PROC_404_SUPPLIER_NOT_FOUND)`.
  - Không nuốt lỗi (`catch (Exception e) {}`).
- **Audit Logging:**
  - Ghi nhật ký các hành động trọng yếu (Tạo đơn, duyệt đơn, hủy đơn) qua `AuditService`.

---

## 🗄 3. QUY TẮC CƠ SỞ DỮ LIỆU & SCHEMA
1. CSDL chuẩn của dự án nằm tại file: `c:\ERP-UTT\erp_schema.sql`.
2. Mọi thay đổi bảng (`ALTER TABLE`, thêm cột, index, dữ liệu phân quyền `permission`) phải được cập nhật vào file `erp_schema.sql` và thông báo ngay trên nhóm chat kỹ thuật.

---

## 🛡 4. QUY ĐỊNH TẠO PULL REQUEST & CODE REVIEW

### 4.1. Reviewer bắt buộc
Khi tạo Pull Request, Dev **bắt buộc gán Reviewer**:
- 👤 `hoangdinhdung05`
- 👤 `Hoàn`

---

### 4.2. Mẫu PR Description (PR Template)
```markdown
### 📌 [MÃ TASK] - TÊN API / TÍNH NĂNG BACKEND
- **Repo:** backend-service
- **Nhánh:** `feature/tên_dev/mã_task` ➔ **Target:** `dev-2`
- **Tác giả:** [Tên Dev]
- **Reviewer:** @hoangdinhdung05, @hoan

---

### 📝 Chi tiết công việc thực hiện
- [x] Tạo Service, Repository, Mapper, Controller cho phân hệ PROC.
- [x] Đã viết JavaDocs đầy đủ 100% cho Controller và Service.
- [x] Kiểm tra phân quyền với `@PreAuthorize("hasAuthority('PROC_...')")`.
- [x] Đã thêm mã lỗi tương ứng trong `ErrorCode.java`.

---

### 🧪 Bằng chứng kiểm thử (Evidence / Postman / Swagger)
- [x] Đã test thành công các kịch bản thành công và lỗi ngoại lệ (Đính kèm ảnh Swagger/Postman hoặc cURL).
- [x] Đã chạy `mvn clean compile` thành công không có lỗi.

---

### ⚠️ Lưu ý CSDL / Phụ thuộc (nếu có)
- [ ] Cần build `core-model` mới nhất trước khi chạy backend.
- [ ] Đã cập nhật file `erp_schema.sql`.
```

---

### 4.3. Điều kiện Tiên quyết để Merge (Definition of Done PR)
1. 🟢 Có **tối thiểu 01 Approval** từ `hoangdinhdung05` hoặc `Hoàn`.
2. 🟢 Resolve 100% review comments.
3. 🟢 Lệnh `mvn clean compile` hoặc test pass 100%.
4. 🟢 Không có xung đột (No merge conflict) với nhánh `dev-2`.
