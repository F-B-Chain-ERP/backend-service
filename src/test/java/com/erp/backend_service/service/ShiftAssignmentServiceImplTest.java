package com.erp.backend_service.service;

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
import com.erp.backend_service.service.impl.ShiftAssignmentServiceImpl;
import com.erp.core.domain.Account;
import com.erp.core.domain.Shift;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftAssignmentServiceImplTest {

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private ShiftReportRepository shiftReportRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private AccountRoleRepository accountRoleRepository;
    @Mock
    private DataScopeHelper dataScopeHelper;

    private ShiftAssignmentServiceImpl service;
    private ShiftAssignment assignment;
    private UUID assignmentId;
    private UUID branchId;
    private UUID accountId;
    private UUID shiftId;

    @BeforeEach
    void setUp() {
        service = new ShiftAssignmentServiceImpl(
                shiftAssignmentRepository,
                shiftRepository,
                shiftReportRepository,
                accountRepository,
                branchRepository,
                accountRoleRepository,
                new ShiftAssignmentMapper(),
                dataScopeHelper
        );

        assignmentId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        shiftId = UUID.randomUUID();

        assignment = new ShiftAssignment();
        assignment.setId(assignmentId);
        assignment.setBranchId(branchId);
        assignment.setAccountId(accountId);
        assignment.setShiftId(shiftId);
        assignment.setWorkDate(LocalDate.now());
        assignment.setStatus("SCHEDULED");
    }

    @Test
    @DisplayName("Nhân viên không cầm két được điểm danh ca của chính mình")
    void checkInAttendance_Success_OwnerNonCash() {
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(accountRoleRepository.findEffectiveAccountIdsByRoleCodesAndBranchId(
                eq(List.of(accountId)), eq(List.of("ADMIN", "ROLE_MANAGER", "ROLE_CASHIER")),
                eq(branchId), any(), any())).thenReturn(List.of());
        when(shiftAssignmentRepository.save(assignment)).thenReturn(assignment);
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(new Shift()));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account()));

        ShiftAssignmentResponse response = service.checkInAttendance(assignmentId, accountId);

        assertEquals("CHECKED_IN", response.status());
        assertEquals(false, response.cashHandler());
    }

    @Test
    @DisplayName("Ca cầm két không được dùng điểm danh để bỏ qua mở ca")
    void checkInAttendance_Fail_CashHandler() {
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(accountRoleRepository.findEffectiveAccountIdsByRoleCodesAndBranchId(
                eq(List.of(accountId)), eq(List.of("ADMIN", "ROLE_MANAGER", "ROLE_CASHIER")),
                eq(branchId), any(), any())).thenReturn(List.of(accountId));

        BaseException ex = assertThrows(BaseException.class,
                () -> service.checkInAttendance(assignmentId, accountId));

        assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nhân viên không được điểm danh ca của người khác")
    void checkInAttendance_Fail_NotOwner() {
        UUID otherUserId = UUID.randomUUID();
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        BaseException ex = assertThrows(BaseException.class,
                () -> service.checkInAttendance(assignmentId, otherUserId));

        assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Quản lý không được điểm danh thay nhân viên (chấm công tự phục vụ)")
    void checkInAttendance_Fail_ManagerOverrideBlocked() {
        UUID managerId = UUID.randomUUID();
        when(shiftAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        BaseException ex = assertThrows(BaseException.class,
                () -> service.checkInAttendance(assignmentId, managerId));

        assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
        verify(shiftAssignmentRepository, never()).save(any());
    }
}
