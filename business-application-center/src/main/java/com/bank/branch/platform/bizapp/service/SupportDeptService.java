package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.event.SupportDispatchedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 中场支持申请服务（承接侧视图）。
 * <p>
 * 提供承接部门视角的操作：派单、转交、办理完成、分页查询。
 * 场景B专用操作（派单）需先通过场景判断。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportDeptService {

    private final SupportRequestMapper supportMapper;
    private final BizStateMachine bizStateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final SupportRequestDTOConverter supportRequestDTOConverter;

    /**
     * 秘书派单（仅场景B）。
     * <p>
     * IN_APPROVAL -> IN_PROGRESS，设置 dispatch_emp_id、dispatch_time、assigned_emp_id。
     * </p>
     *
     * @param id              申请ID
     * @param assignedEmpId   被派工号
     * @param dispatcherEmpId 派单人工号（部门秘书）
     * @param dispatchRemark  派单备注（可选，文档 §D.2）
     */
    @Transactional
    public void dispatch(String id, String assignedEmpId, String dispatcherEmpId, String dispatchRemark) {
        log.info("[SupportDeptService.dispatch] id={}, assignedEmpId={}, dispatcher={}",
                id, assignedEmpId, dispatcherEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        bizStateMachine.validateSupportTransition(request.getStatus(), SupportStatus.IN_PROGRESS.getCode());

        LocalDateTime now = LocalDateTime.now();
        request.setStatus(SupportStatus.IN_PROGRESS.getCode());
        request.setAssignedEmpId(assignedEmpId);
        request.setDispatchEmpId(dispatcherEmpId);
        request.setDispatchTime(now);
        request.setUpdatedBy(dispatcherEmpId);
        request.setUpdatedTime(now);
        supportMapper.updateById(request);

        // 发布派单事件（携带 dispatchRemark，文档 §8.5）
        eventPublisher.publishEvent(new SupportDispatchedEvent(
                id, request.getRequestNo(), assignedEmpId, dispatcherEmpId, request.getSupportDeptId(), dispatchRemark
        ));

        log.info("[SupportDeptService.dispatch] 申请 {} 已派单给 {}", id, assignedEmpId);
    }

    /**
     * 转交承接人（高危操作）。
     * <p>
     * 只有当前 assignedEmpId 才能发起转交，转交后 assigned_emp_id 更新。
     * </p>
     *
     * @param id               申请ID
     * @param newAssignedEmpId 新承接人工号
     * @param operatorEmpId    操作人（必须是当前承接人）
     */
    @Transactional
    public void transfer(String id, String newAssignedEmpId, String operatorEmpId) {
        log.info("[SupportDeptService.transfer] id={}, newAssigned={}, operator={}", id, newAssignedEmpId, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        // 只有当前承接人才能转交
        if (!operatorEmpId.equals(request.getAssignedEmpId())) {
            throw new BizException(
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage()
            );
        }

        request.setAssignedEmpId(newAssignedEmpId);
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        log.info("[SupportDeptService.transfer] 申请 {} 已转交给 {}", id, newAssignedEmpId);
    }

    /**
     * 办理完成。
     * <p>
     * 场景A：IN_APPROVAL -> COMPLETED；
     * 场景B：IN_PROGRESS -> COMPLETED（success=true）或 REJECTED（success=false）。
     * </p>
     *
     * @param id            申请ID
     * @param success       true=成功完成，false=拒绝
     * @param operatorEmpId 操作人
     */
    @Transactional
    public void complete(String id, boolean success, String operatorEmpId) {
        log.info("[SupportDeptService.complete] id={}, success={}, operator={}", id, success, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        String targetStatus = success ? SupportStatus.COMPLETED.getCode() : SupportStatus.REJECTED.getCode();
        bizStateMachine.validateSupportTransition(request.getStatus(), targetStatus);

        request.setStatus(targetStatus);
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        // 发布完成事件
        eventPublisher.publishEvent(new SupportCompletedEvent(
                id, request.getRequestNo(), request.getCustId(),
                request.getProductId(), request.getAssignedEmpId(), success
        ));

        log.info("[SupportDeptService.complete] 申请 {} 办理完成，状态={}", id, targetStatus);
    }

    /**
     * 承接侧分页查询（返回 Entity，供内部使用）。
     *
     * @param supportDeptId 承接部门ID
     * @param status        状态筛选
     * @param assignedEmpId 承接人工号筛选
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @return 分页结果（Entity）
     */
    public PageResult<SupportRequest> listPageForDept(String supportDeptId, String status,
                                                       String assignedEmpId, int pageNo, int pageSize) {
        log.info("[SupportDeptService.listPageForDept] deptId={}, pageNo={}, pageSize={}",
                supportDeptId, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<SupportRequest> records = supportMapper.selectPageForDept(supportDeptId, status, assignedEmpId, offset, pageSize);
        long total = supportMapper.countPageForDept(supportDeptId, status, assignedEmpId);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 承接侧分页查询（返回 ListItemDTO，供 REST 层使用）。
     * <p>
     * 通过 {@link SupportRequestDTOConverter#toListItems} 批量转换并填充展示字段，避免 N+1 查询。
     * REST 层禁止直接暴露 Entity，统一通过此方法获取列表数据。
     * </p>
     *
     * @param supportDeptId 承接部门ID
     * @param status        状态筛选
     * @param assignedEmpId 承接人工号筛选
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @return 分页结果（ListItemDTO，不含 deleted 等内部字段）
     */
    public PageResult<SupportRequestListItemDTO> listPageForDeptAsDTO(String supportDeptId, String status,
                                                                      String assignedEmpId, int pageNo, int pageSize) {
        PageResult<SupportRequest> page = listPageForDept(supportDeptId, status, assignedEmpId, pageNo, pageSize);
        List<SupportRequestListItemDTO> items = supportRequestDTOConverter.toListItems(page.getRecords());
        return PageResult.of(pageNo, pageSize, page.getTotal(), items);
    }
}
