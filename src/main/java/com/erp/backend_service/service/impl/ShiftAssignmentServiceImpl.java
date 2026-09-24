package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ShiftAssignmentMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.ShiftRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.ShiftAssignmentService;
import com.erp.core.domain.Account;
import com.erp.core.domain.Shift;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.dto.request.store.BulkAssignShiftRequest;
import com.erp.core.dto.request.store.CreateShiftAssignmentRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import com.erp.core.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ShiftAssignmentServiceImpl implements ShiftAssignmentService {

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final AccountRepository accountRepository;
    private final BranchRepository branchRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final DataScopeHelper dataScopeHelper;

    // Vai trò được cầm két: thu ngân, quản lý, admin. Pha chế và vai khác
    // phân ca thoải mái, không check trùng giờ.
    private static final List<String> CASH_ROLE_CODES = List.of("ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    private static final List<String> MANAGER_ROLE_CODES = List.of("ADMIN", "ROLE_MANAGER");
    // Nhóm vai pha chế / phục vụ (không két). Dùng để phát hiện tài khoản
    // giữ đồng thời 2 nghiệp vụ Barista + Thu ngân (dễ bị đánh nhau luồng
    // check-in vs mở két) — danh sách role lấy theo 016-seed-pos-permissions.
    private static final List<String> BARISTA_ROLE_CODES =
            List.of("ROLE_BARISTA", "ROLE_USER", "STAFF", "ROLE_STAFF");

    public ShiftAssignmentServiceImpl(ShiftAssignmentRepository shiftAssignmentRepository,
                                       ShiftRepository shiftRepository,
                                       ShiftReportRepository shiftReportRepository,
                                       AccountRepository accountRepository,
                                       BranchRepository branchRepository,
                                       AccountRoleRepository accountRoleRepository,
                                       ShiftAssignmentMapper shiftAssignmentMapper,
                                       DataScopeHelper dataScopeHelper) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftRepository = shiftRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.accountRepository = accountRepository;
        this.branchRepository = branchRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.shiftAssignmentMapper = shiftAssignmentMapper;
        this.dataScopeHelper = dataScopeHelper;
    }

    private boolean hasEffectiveRole(UUID accountId, UUID branchId, List<String> roleCodes) {
        if (accountId == null || branchId == null || roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return !accountRoleRepository.findEffectiveAccountIdsByRoleCodesAndBranchId(
                List.of(accountId), roleCodes, branchId, EntityStatus.ACTIVE, Instant.now()).isEmpty();
    }

    private boolean isCashHandler(UUID accountId, UUID branchId) {
        return hasEffectiveRole(accountId, branchId, CASH_ROLE_CODES);
    }

    private boolean isManagerOrAdmin(UUID accountId, UUID branchId) {
        return hasEffectiveRole(accountId, branchId, MANAGER_ROLE_CODES);
    }

    private void enforceAttendanceAccess(ShiftAssignment assignment, UUID currentUserId) {
        // Chấm công (điểm danh vào/ra) là tự phục vụ: chỉ chủ ca được bấm.
        // Quản lý/admin KHÔNG được chấm hộ (ngoài đời không thể) — muốn hỗ trợ
        // ca két thì dùng luồng mở/đóng hộ bên ShiftOperationService.
        boolean ownsAssignment = currentUserId != null && currentUserId.equals(assignment.getAccountId());
        if (!ownsAssignment) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED,
                    "Chấm công phải do chính nhân viên thực hiện, quản lý không được chấm hộ");
        }
    }

    // Tài khoản giữ đồng thời 2 nghiệp vụ (vừa Barista vừa Thu ngân) sẽ bị
    // đánh nhau luồng check-in vs mở két. Chặn từ lúc phân ca để buộc tách
    // thành 2 tài khoản (hoặc gỡ 1 role). Miễn trừ quản lý/admin vì họ cần
    // mở/đóng hộ khi thu ngân quên.
    private boolean isDualRoleAccount(UUID accountId, UUID branchId) {
        if (accountId == null || branchId == null) {
            return false;
        }
        if (isManagerOrAdmin(accountId, branchId)) {
            return false;
        }
        return isCashHandler(accountId, branchId)
                && hasEffectiveRole(accountId, branchId, BARISTA_ROLE_CODES);
    }

    private void validateSingleRole(UUID accountId, UUID branchId) {
        if (isDualRoleAccount(accountId, branchId)) {
            throw new BaseException(ErrorCode.STORE_409_ASSIGNMENT_EXISTS,
                    "Tài khoản đang giữ đồng thời 2 quyền Barista và Thu ngân nên không thể phân ca. "
                            + "Vui lòng tách thành 2 tài khoản (hoặc gỡ bớt 1 vai trò) rồi phân lại");
        }
    }

    // NOTE(tính công sau): checkInAt/checkOutAt + workDate + giờ khung đã đủ
    // giờ làm, phase sau chỉ việc cộng giờ × lương.
    private Map<UUID, Boolean> cashHandlerFlags(java.util.Collection<UUID> accountIds, UUID branchId) {
        Map<UUID, Boolean> flags = new java.util.HashMap<>();
        if (accountIds == null || accountIds.isEmpty() || branchId == null) {
            return flags;
        }
        Set<UUID> cashAccounts = new HashSet<>(accountRoleRepository
                .findEffectiveAccountIdsByRoleCodesAndBranchId(
                        accountIds, CASH_ROLE_CODES, branchId, EntityStatus.ACTIVE, Instant.now()));
        for (UUID id : accountIds) {
            flags.put(id, cashAccounts.contains(id));
        }
        return flags;
    }

    // Hai khung giờ có đè nhau không (xử lý ca qua đêm, chạm mốc tính là không đè).
    private static boolean timesOverlap(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        if (s1 == null || e1 == null || s2 == null || e2 == null) {
            return false;
        }
        List<int[]> a = splitSegments(s1, e1);
        List<int[]> b = splitSegments(s2, e2);
        for (int[] x : a) {
            for (int[] y : b) {
                if (x[0] < y[1] && y[0] < x[1]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<int[]> splitSegments(LocalTime start, LocalTime end) {
        int s = start.toSecondOfDay() / 60;
        int e = end.toSecondOfDay() / 60;
        if (e <= s) {
            return List.of(new int[]{s, 1440}, new int[]{0, e});
        }
        return List.of(new int[]{s, e});
    }

    // Tìm khung ca của thu ngân khác bị đè giờ (bỏ qua CANCELLED/ABSENT).
    // Pha chế hai đầu thì thoải mái. Trả về khung bị đè hoặc null.
    private Shift findCashOverlap(UUID branchId, Shift newShift, UUID accountId, LocalDate workDate,
                                  List<ShiftAssignment> extra, Map<UUID, Shift> extraShifts) {
        if (!isCashHandler(accountId, branchId)) {
            return null;
        }
        List<ShiftAssignment> sameDay = new ArrayList<>(
                shiftAssignmentRepository.findByBranchIdAndWorkDate(branchId, workDate));
        if (extra != null) {
            sameDay.addAll(extra);
        }
        for (ShiftAssignment e : sameDay) {
            if ("CANCELLED".equalsIgnoreCase(e.getStatus()) || "ABSENT".equalsIgnoreCase(e.getStatus())) {
                continue;
            }
            if (newShift.getId() != null && newShift.getId().equals(e.getShiftId())
                    && accountId.equals(e.getAccountId())) {
                continue;
            }
            Shift es = extraShifts != null ? extraShifts.get(e.getShiftId()) : null;
            if (es == null) {
                es = shiftRepository.findById(e.getShiftId()).orElse(null);
            }
            if (es == null || !isCashHandler(e.getAccountId(), branchId)) {
                continue;
            }
            if (timesOverlap(newShift.getStartTime(), newShift.getEndTime(),
                    es.getStartTime(), es.getEndTime())) {
                return es;
            }
        }
        return null;
    }

    @Override
    public ShiftAssignmentResponse assignShift(CreateShiftAssignmentRequest request) {
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Shift shift = shiftRepository.findByIdAndBranchId(request.shiftId(), request.branchId())
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_SHIFT_NOT_FOUND));

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 1 tài khoản chỉ giữ 1 nghiệp vụ (Barista HOẶC Thu ngân) để khỏi đánh nhau.
        validateSingleRole(request.accountId(), request.branchId());

        // 1 người 1 ca/ngày: chỉ tính ca còn hiệu lực (bỏ qua ca đã Hủy/Vắng để phân lại).
        if (shiftAssignmentRepository.existsByShiftIdAndAccountIdAndWorkDateAndStatusNotIn(
                request.shiftId(), request.accountId(), request.workDate(), List.of("CANCELLED", "ABSENT"))) {
            throw new BaseException(ErrorCode.STORE_409_ASSIGNMENT_EXISTS);
        }

        if (shiftAssignmentRepository.existsByBranchIdAndAccountIdAndWorkDateAndStatusNotIn(
                request.branchId(), request.accountId(), request.workDate(), List.of("CANCELLED", "ABSENT"))) {
            throw new BaseException(ErrorCode.STORE_409_ASSIGNMENT_EXISTS);
        }

        // Thu ngân đè giờ thu ngân khác cùng ngày thì chặn từ lúc phân ca.
        Shift conflict = findCashOverlap(request.branchId(), shift, request.accountId(), request.workDate(),
                null, null);
        if (conflict != null) {
            throw new BaseException(ErrorCode.STORE_409_ASSIGNMENT_EXISTS,
                    "Ca của thu ngân bị trùng giờ với ca " + conflict.getShiftCode() + " ngày " + request.workDate());
        }

        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setShiftId(request.shiftId());
        assignment.setBranchId(request.branchId());
        assignment.setAccountId(request.accountId());
        assignment.setWorkDate(request.workDate());
        assignment.setStatus("SCHEDULED");
        assignment.setNote(request.note());

        ShiftAssignment saved = shiftAssignmentRepository.save(assignment);
        return shiftAssignmentMapper.toResponse(saved, shift, account,
                isCashHandler(request.accountId(), request.branchId()));
    }

    @Override
    public List<ShiftAssignmentResponse> bulkAssignShifts(BulkAssignShiftRequest request) {
        dataScopeHelper.enforceBranchAccess(request.branchId());

        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (request.assignments() != null && request.assignments().size() > 100) {
            throw new BaseException(ErrorCode.BAD_REQUEST);
        }

        List<ShiftAssignment> toSave = new ArrayList<>();
        Set<String> seenAccountDate = new HashSet<>();
        var itemShiftIds = request.assignments().stream().map(BulkAssignShiftRequest.AssignmentItem::shiftId).distinct().toList();
        Map<UUID, Shift> itemShiftMap = shiftRepository.findAllById(itemShiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity(), (a, b) -> a));
        for (BulkAssignShiftRequest.AssignmentItem item : request.assignments()) {
            Shift itemShift = itemShiftMap.get(item.shiftId());
            if (itemShift == null || !request.branchId().equals(itemShift.getBranchId())) {
                throw new BaseException(ErrorCode.STORE_404_SHIFT_NOT_FOUND);
            }
            if (!accountRepository.existsById(item.accountId())) {
                throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
            }
            // Tài khoản 2 vai thì bỏ qua như trùng (báo số bỏ qua ở FE).
            if (isDualRoleAccount(item.accountId(), request.branchId())) {
                continue;
            }
            String key = item.accountId() + "|" + item.workDate();
            if (!seenAccountDate.add(key)) {
                continue;
            }
            if (shiftAssignmentRepository.existsByShiftIdAndAccountIdAndWorkDateAndStatusNotIn(
                    item.shiftId(), item.accountId(), item.workDate(), List.of("CANCELLED", "ABSENT"))) {
                continue;
            }
            if (shiftAssignmentRepository.existsByBranchIdAndAccountIdAndWorkDateAndStatusNotIn(
                    request.branchId(), item.accountId(), item.workDate(), List.of("CANCELLED", "ABSENT"))) {
                continue;
            }
            // Thu ngân đè giờ thu ngân khác thì bỏ qua như trùng (báo số bỏ qua ở FE).
            if (findCashOverlap(request.branchId(), itemShift, item.accountId(), item.workDate(),
                    toSave, itemShiftMap) != null) {
                continue;
            }

            ShiftAssignment assignment = new ShiftAssignment();
            assignment.setShiftId(item.shiftId());
            assignment.setBranchId(request.branchId());
            assignment.setAccountId(item.accountId());
            assignment.setWorkDate(item.workDate());
            assignment.setStatus("SCHEDULED");
            assignment.setNote(item.note());
            toSave.add(assignment);
        }

        List<ShiftAssignment> saved = shiftAssignmentRepository.saveAll(toSave);

        // Batch load shifts và accounts để map kết quả
        var shiftIds = saved.stream().map(ShiftAssignment::getShiftId).distinct().toList();
        var accountIds = saved.stream().map(ShiftAssignment::getAccountId).distinct().toList();

        Map<UUID, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
        Map<UUID, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, Boolean> cashFlags = cashHandlerFlags(accountIds, request.branchId());

        return saved.stream()
                .map(a -> shiftAssignmentMapper.toResponse(a, shiftMap.get(a.getShiftId()), accountMap.get(a.getAccountId()),
                        cashFlags.getOrDefault(a.getAccountId(), false)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftAssignmentResponse getAssignmentById(UUID id) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());

        Shift shift = shiftRepository.findById(assignment.getShiftId()).orElse(null);
        Account account = accountRepository.findById(assignment.getAccountId()).orElse(null);

        return shiftAssignmentMapper.toResponse(assignment, shift, account,
                isCashHandler(assignment.getAccountId(), assignment.getBranchId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShiftAssignmentResponse> searchAssignments(UUID branchId,
                                                                  LocalDate startDate,
                                                                  LocalDate endDate,
                                                                  UUID accountId,
                                                                  String status,
                                                                  Pageable pageable) {
        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);

        Specification<ShiftAssignment> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (startDate != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("workDate"), startDate));
            }
            if (endDate != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("workDate"), endDate));
            }
            if (accountId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("accountId"), accountId));
            }
            if (status != null && !status.isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), status.trim().toUpperCase()));
            }
            return predicates;
        };

        Page<ShiftAssignment> page = shiftAssignmentRepository.findAll(spec, pageable);
        var assignments = page.getContent();

        var shiftIds = assignments.stream().map(ShiftAssignment::getShiftId).distinct().toList();
        var accountIds = assignments.stream().map(ShiftAssignment::getAccountId).distinct().toList();

        Map<UUID, Shift> shiftMap = shiftRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
        Map<UUID, Account> accountMap = accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        List<ShiftAssignmentResponse> items = assignments.stream()
                .map(a -> shiftAssignmentMapper.toResponse(a, shiftMap.get(a.getShiftId()), accountMap.get(a.getAccountId()),
                        isCashHandler(a.getAccountId(), a.getBranchId())))
                .toList();

        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), items);
    }

    @Override
    public void cancelAssignment(UUID id, String reason) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());

        if (!"SCHEDULED".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        assignment.setStatus("CANCELLED");
        if (reason != null && !reason.isBlank()) {
            assignment.setNote(assignment.getNote() != null ? assignment.getNote() + " | Lý do hủy: " + reason : "Lý do hủy: " + reason);
        }
        shiftAssignmentRepository.save(assignment);
    }

    @Override
    public ShiftAssignmentResponse checkInAttendance(UUID id, UUID currentUserId) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());
        enforceAttendanceAccess(assignment, currentUserId);

        // Tự checkout ca pha chế kẹt qua đêm của chính owner (không két, không tiền,
        // chỉ là timestamp chấm công) để ca mới điểm danh được.
        shiftAssignmentRepository.findFirstByAccountIdAndStatus(assignment.getAccountId(), "CHECKED_IN")
                .filter(other -> !other.getId().equals(assignment.getId()))
                .filter(other -> other.getWorkDate() != null && other.getWorkDate().isBefore(LocalDate.now()))
                .filter(other -> other.getInitialCash() == null
                        && !isCashHandler(other.getAccountId(), other.getBranchId()))
                .ifPresent(other -> {
                    other.setStatus("CHECKED_OUT");
                    other.setCheckOutAt(java.time.Instant.now());
                    String tag = "Hệ thống tự đóng ca quá hạn (quên checkout)";
                    other.setNote(other.getNote() != null ? other.getNote() + " | " + tag : tag);
                    shiftAssignmentRepository.save(other);
                });

        if (isCashHandler(assignment.getAccountId(), assignment.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED, "Ca cầm két phải sử dụng chức năng mở ca");
        }

        if (!"SCHEDULED".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        assignment.setStatus("CHECKED_IN");
        assignment.setCheckInAt(java.time.Instant.now());
        ShiftAssignment saved = shiftAssignmentRepository.save(assignment);

        Shift shift = shiftRepository.findById(saved.getShiftId()).orElse(null);
        Account account = accountRepository.findById(saved.getAccountId()).orElse(null);
        return shiftAssignmentMapper.toResponse(saved, shift, account,
                false);
    }

    @Override
    public ShiftAssignmentResponse checkOutAttendance(UUID id, UUID currentUserId) {
        ShiftAssignment assignment = shiftAssignmentRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.STORE_404_ASSIGNMENT_NOT_FOUND));

        dataScopeHelper.enforceBranchAccess(assignment.getBranchId());
        enforceAttendanceAccess(assignment, currentUserId);

        if (isCashHandler(assignment.getAccountId(), assignment.getBranchId())) {
            throw new BaseException(ErrorCode.PERMISSION_DENIED, "Ca cầm két phải sử dụng chức năng đóng ca");
        }

        if (!"CHECKED_IN".equalsIgnoreCase(assignment.getStatus())) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        // Ca két phải chốt két (sinh biên bản), không điểm danh ra.
        if (shiftReportRepository.findByAssignmentId(assignment.getId()).isPresent()) {
            throw new BaseException(ErrorCode.STORE_400_INVALID_STATUS_TRANSITION);
        }

        assignment.setStatus("CHECKED_OUT");
        assignment.setCheckOutAt(java.time.Instant.now());
        ShiftAssignment saved = shiftAssignmentRepository.save(assignment);

        Shift shift = shiftRepository.findById(saved.getShiftId()).orElse(null);
        Account account = accountRepository.findById(saved.getAccountId()).orElse(null);
        return shiftAssignmentMapper.toResponse(saved, shift, account,
                false);
    }
}
