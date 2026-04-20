package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.LoanStatus;
import com.bank.branch.platform.bizapp.event.LoanSubmittedEvent;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 资产投放申请业务服务。
 * <p>
 * 负责贷款申请的创建草稿、编辑、删除、提交审批、撤回及分页查询。
 * 状态机流转：DRAFT -> IN_APPROVAL -> COMPLETED/REJECTED/CANCELLED。
 * 提交审批时通过 {@link WorkflowApi} 启动流程，业务键格式为 {@code LOAN:{id}}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanApplyMapper loanMapper;
    private final BizStateMachine bizStateMachine;
    private final BizNoGenerator bizNoGenerator;
    private final WorkflowApi workflowApi;
    private final CustomerQueryApi customerQueryApi;
    private final TouchTaskQueryApi touchTaskQueryApi;
    private final ApplicationEventPublisher eventPublisher;

    /** 贷款审批流程定义Key */
    private static final String PROCESS_DEFINITION_KEY = "loan_approve_v1";

    /**
     * 创建贷款申请草稿。
     * <p>
     * 校验客户有效性、机构认领关系、触达任务归属，生成唯一业务编号，初始状态 DRAFT。
     * </p>
     *
     * @param custId                客户ID
     * @param sourceTouchTaskId     来源触达任务ID（可空）
     * @param projectType           项目类型
     * @param bizType               业务类型
     * @param guaranteeType         担保方式
     * @param creditAmount          授信金额
     * @param creditExposureAmount  敞口金额
     * @param operatorEmpId         操作人工号
     * @param orgCode               操作人归属机构
     * @return 新建的贷款申请实体
     */
    @Transactional
    public LoanApply createDraft(String custId, String sourceTouchTaskId,
                                  String projectType, String bizType, String guaranteeType,
                                  BigDecimal creditAmount, BigDecimal creditExposureAmount,
                                  String operatorEmpId, String orgCode) {
        log.info("[LoanService.createDraft] custId={}, operator={}, org={}", custId, operatorEmpId, orgCode);

        // 1. 校验客户有效性
        if (!customerQueryApi.isValidCustomer(custId)) {
            throw new BizException(
                    BizAppErrorCode.CUSTOMER_NOT_VALID.getCode(),
                    BizAppErrorCode.CUSTOMER_NOT_VALID.getMessage()
            );
        }

        // 2. 校验机构已认领该客户
        if (!customerQueryApi.isClaimedByOrg(custId, orgCode)) {
            throw new BizException(
                    BizAppErrorCode.CUSTOMER_NOT_CLAIMED_BY_ORG.getCode(),
                    BizAppErrorCode.CUSTOMER_NOT_CLAIMED_BY_ORG.getMessage()
            );
        }

        // 3. 如果有来源触达任务，校验执行人是否为当前操作人
        if (sourceTouchTaskId != null) {
            TouchTask task = touchTaskQueryApi.getTaskById(sourceTouchTaskId);
            if (task == null || !operatorEmpId.equals(task.getAssigneeEmpId())) {
                throw new BizException(
                        BizAppErrorCode.NOT_TOUCH_TASK_ASSIGNEE.getCode(),
                        BizAppErrorCode.NOT_TOUCH_TASK_ASSIGNEE.getMessage()
                );
            }
        }

        // 4. 校验敞口金额不超过授信金额
        validateExposureAmount(creditAmount, creditExposureAmount);

        // 5. 构建实体并插入
        String id = UUID.randomUUID().toString().replace("-", "");
        String applyNo = bizNoGenerator.generateLoanNo();
        LocalDateTime now = LocalDateTime.now();

        LoanApply entity = new LoanApply();
        entity.setId(id);
        entity.setApplyNo(applyNo);
        entity.setCustId(custId);
        entity.setSourceTouchTaskId(sourceTouchTaskId);
        entity.setProjectType(projectType);
        entity.setBizType(bizType);
        entity.setGuaranteeType(guaranteeType);
        entity.setCreditAmount(creditAmount);
        entity.setCreditExposureAmount(creditExposureAmount);
        entity.setStatus(LoanStatus.DRAFT.getCode());
        entity.setOwnerOrgId(orgCode);
        entity.setCreatedBy(operatorEmpId);
        entity.setUpdatedBy(operatorEmpId);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        entity.setDeleted(0);

        loanMapper.insert(entity);
        log.info("[LoanService.createDraft] 创建成功 id={}, applyNo={}", id, applyNo);
        return entity;
    }

    /**
     * 更新贷款申请草稿。
     * <p>
     * 只允许草稿状态且由创建人操作。
     * </p>
     *
     * @param id                    申请ID
     * @param projectType           项目类型（可空，空则不更新）
     * @param bizType               业务类型（可空）
     * @param guaranteeType         担保方式（可空）
     * @param creditAmount          授信金额（可空）
     * @param creditExposureAmount  敞口金额（可空）
     * @param operatorEmpId         操作人工号
     * @return 更新后的贷款申请实体
     */
    @Transactional
    public LoanApply updateDraft(String id, String projectType, String bizType, String guaranteeType,
                                  BigDecimal creditAmount, BigDecimal creditExposureAmount,
                                  String operatorEmpId) {
        log.info("[LoanService.updateDraft] id={}, operator={}", id, operatorEmpId);

        // 1. 查询申请
        LoanApply existing = selectByIdOrThrow(id);

        // 2. 校验草稿状态
        validateDraftStatus(existing);

        // 3. 校验是创建人
        validateCreator(existing, operatorEmpId);

        // 4. 校验敞口金额
        // 以更新值为准，若更新值为空则用已有值
        BigDecimal finalCredit = creditAmount != null ? creditAmount : existing.getCreditAmount();
        BigDecimal finalExposure = creditExposureAmount != null ? creditExposureAmount : existing.getCreditExposureAmount();
        validateExposureAmount(finalCredit, finalExposure);

        // 5. 构建部分更新实体
        LoanApply update = new LoanApply();
        update.setId(id);
        update.setProjectType(projectType);
        update.setBizType(bizType);
        update.setGuaranteeType(guaranteeType);
        update.setCreditAmount(creditAmount);
        update.setCreditExposureAmount(creditExposureAmount);
        update.setUpdatedBy(operatorEmpId);
        update.setUpdatedTime(LocalDateTime.now());
        loanMapper.updateById(update);

        // 返回合并后的实体
        if (projectType != null) existing.setProjectType(projectType);
        if (bizType != null) existing.setBizType(bizType);
        if (guaranteeType != null) existing.setGuaranteeType(guaranteeType);
        if (creditAmount != null) existing.setCreditAmount(creditAmount);
        if (creditExposureAmount != null) existing.setCreditExposureAmount(creditExposureAmount);
        return existing;
    }

    /**
     * 逻辑删除贷款申请草稿（软删除，设置 deleted=1）。
     * <p>
     * 只允许草稿状态且由创建人操作。
     * </p>
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人工号
     */
    @Transactional
    public void deleteDraft(String id, String operatorEmpId) {
        log.info("[LoanService.deleteDraft] id={}, operator={}", id, operatorEmpId);

        LoanApply existing = selectByIdOrThrow(id);
        validateDraftStatus(existing);
        validateCreator(existing, operatorEmpId);

        LoanApply update = new LoanApply();
        update.setId(id);
        update.setDeleted(1);
        update.setUpdatedBy(operatorEmpId);
        update.setUpdatedTime(LocalDateTime.now());
        loanMapper.updateById(update);
    }

    /**
     * 提交贷款申请审批。
     * <p>
     * 使用 SELECT FOR UPDATE 防并发提交，启动 Flowable 工作流，
     * 状态更新为 IN_APPROVAL，发布 LoanSubmittedEvent。
     * </p>
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人工号
     * @param orgCode       操作人归属机构
     */
    @Transactional
    public void submitForApproval(String id, String operatorEmpId, String orgCode) {
        log.info("[LoanService.submitForApproval] id={}, operator={}", id, operatorEmpId);

        // 1. SELECT FOR UPDATE 防并发
        LoanApply existing = loanMapper.selectForUpdate(id);
        if (existing == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        // 2. 校验状态迁移 DRAFT -> IN_APPROVAL
        bizStateMachine.validateLoanTransition(existing.getStatus(), LoanStatus.IN_APPROVAL.getCode());

        // 3. 校验创建人
        validateCreator(existing, operatorEmpId);

        // 4. 构建并启动工作流
        String businessKey = "LOAN:" + id;
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("LOAN");
        cmd.setBizId(id);
        cmd.setBusinessKey(businessKey);
        cmd.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        cmd.setStartUser(operatorEmpId);
        cmd.setStartOrgId(orgCode);
        cmd.setTitle("贷款申请审批-" + existing.getApplyNo());

        WorkflowLaunchResp resp;
        try {
            resp = workflowApi.startProcess(cmd);
        } catch (Exception e) {
            log.error("[LoanService.submitForApproval] 工作流启动失败 id={}", id, e);
            throw new BizException(
                    BizAppErrorCode.WORKFLOW_CALL_ERROR.getCode(),
                    BizAppErrorCode.WORKFLOW_CALL_ERROR.getMessage()
            );
        }

        // 5. 更新状态、businessKey、processInstanceId
        LoanApply update = new LoanApply();
        update.setId(id);
        update.setStatus(LoanStatus.IN_APPROVAL.getCode());
        update.setBusinessKey(businessKey);
        update.setProcessInstanceId(resp.getProcessInstanceId());
        update.setUpdatedBy(operatorEmpId);
        update.setUpdatedTime(LocalDateTime.now());
        loanMapper.updateById(update);

        // 6. 发布提交事件
        eventPublisher.publishEvent(new LoanSubmittedEvent(
                id, existing.getApplyNo(), existing.getCustId(), orgCode, operatorEmpId
        ));
    }

    /**
     * 撤回贷款申请。
     * <p>
     * 撤回允许在 IN_APPROVAL 状态，由创建人操作。
     * </p>
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人工号
     */
    @Transactional
    public void cancelApply(String id, String operatorEmpId) {
        log.info("[LoanService.cancelApply] id={}, operator={}", id, operatorEmpId);

        LoanApply existing = selectByIdOrThrow(id);
        bizStateMachine.validateLoanTransition(existing.getStatus(), LoanStatus.CANCELLED.getCode());
        validateCreator(existing, operatorEmpId);

        loanMapper.updateStatusById(id, LoanStatus.CANCELLED.getCode(), operatorEmpId);
    }

    /**
     * 按ID查询贷款申请详情。
     *
     * @param id 申请ID
     * @return 贷款申请实体
     * @throws BizException BIZ-40401 如果不存在
     */
    public LoanApply getById(String id) {
        return selectByIdOrThrow(id);
    }

    /**
     * 分页查询贷款申请列表。
     *
     * @param keyword    关键词（模糊匹配申请编号/客户名称）
     * @param status     状态过滤（可空）
     * @param ownerOrgId 归属机构过滤（可空）
     * @param pageNo     页码（从1开始）
     * @param pageSize   每页大小
     * @return 分页结果
     */
    public PageResult<LoanApply> listPage(String keyword, String status,
                                           String ownerOrgId, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<LoanApply> records = loanMapper.selectPage(keyword, status, ownerOrgId, offset, pageSize);
        long total = loanMapper.countPage(keyword, status, ownerOrgId);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    // ==================== 私有工具方法 ====================

    /**
     * 按ID查询，不存在则抛出 BIZ-40401。
     */
    private LoanApply selectByIdOrThrow(String id) {
        LoanApply entity = loanMapper.selectById(id);
        if (entity == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }
        return entity;
    }

    /**
     * 校验申请为草稿状态，否则抛出 BIZ-42303。
     */
    private void validateDraftStatus(LoanApply entity) {
        if (!LoanStatus.DRAFT.getCode().equals(entity.getStatus())) {
            throw new BizException(
                    BizAppErrorCode.NOT_DRAFT_STATUS.getCode(),
                    BizAppErrorCode.NOT_DRAFT_STATUS.getMessage()
            );
        }
    }

    /**
     * 校验操作人是创建人，否则抛出 BIZ-40305。
     */
    private void validateCreator(LoanApply entity, String operatorEmpId) {
        if (!operatorEmpId.equals(entity.getCreatedBy())) {
            throw new BizException(
                    BizAppErrorCode.NOT_APPLY_CREATOR.getCode(),
                    BizAppErrorCode.NOT_APPLY_CREATOR.getMessage()
            );
        }
    }

    /**
     * 校验敞口金额不超过授信金额（两者均不为空时才校验）。
     */
    private void validateExposureAmount(BigDecimal creditAmount, BigDecimal creditExposureAmount) {
        if (creditAmount != null && creditExposureAmount != null
                && creditExposureAmount.compareTo(creditAmount) > 0) {
            throw new BizException(
                    BizAppErrorCode.EXPOSURE_EXCEEDS_CREDIT.getCode(),
                    BizAppErrorCode.EXPOSURE_EXCEEDS_CREDIT.getMessage()
            );
        }
    }
}
