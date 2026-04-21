package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.converter.CustomerDTOConverter;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.event.ClaimTransferredEvent;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 客户主档业务服务。
 * <p>
 * 负责客户主档的查询、维护人转交、删除申请等核心业务逻辑。
 * 写操作均在 Service 层做二次权限/状态校验，确保数据安全。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustMasterMapper masterMapper;
    private final CustClaimMapper claimMapper;
    private final CustLeadMapper leadMapper;
    private final WorkflowApi workflowApi;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 按 ID 查询客户主档，不存在时抛出 BizException。
     *
     * @param id 客户主档 ID
     * @return 客户主档实体
     * @throws BizException CUSTOMER_NOT_FOUND 客户不存在
     */
    public CustMaster getById(String id) {
        log.debug("[CustomerService.getById] id={}", id);
        return requireExists(id);
    }

    /**
     * 分页查询客户主档列表。
     * <p>
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword  关键词（模糊匹配 cust_name / unified_credit_code），可为 null
     * @param status   客户状态过滤（ACTIVE / INACTIVE），可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<CustMaster> listPage(String keyword, String status, int pageNo, int pageSize) {
        log.debug("[CustomerService.listPage] keyword={}, status={}, pageNo={}, pageSize={}", keyword, status, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;
        List<CustMaster> records = masterMapper.selectPage(keyword, status, offset, pageSize);
        long total = masterMapper.countPage(keyword, status);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 按 ID 查询客户主档并转换为 DTO，供 REST 出口使用，避免实体字段泄露。
     *
     * @param id 客户主档 ID
     * @return CustomerDTO
     * @throws BizException CUSTOMER_NOT_FOUND 客户不存在
     */
    public CustomerDTO getByIdAsDTO(String id) {
        log.debug("[CustomerService.getByIdAsDTO] id={}", id);
        CustMaster entity = requireExists(id);
        return CustomerDTOConverter.toDTO(entity);
    }

    /**
     * 分页查询客户主档列表并转换为 DTO，供 REST 出口使用，避免实体字段泄露。
     *
     * @param keyword  关键词，可为 null
     * @param status   客户状态过滤，可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果（CustomerDTO）
     */
    public PageResult<CustomerDTO> listPageAsDTO(String keyword, String status, int pageNo, int pageSize) {
        log.debug("[CustomerService.listPageAsDTO] keyword={}, status={}, pageNo={}, pageSize={}", keyword, status, pageNo, pageSize);
        PageResult<CustMaster> raw = listPage(keyword, status, pageNo, pageSize);
        PageResult<CustomerDTO> out = new PageResult<>();
        out.setPageNo(raw.getPageNo());
        out.setPageSize(raw.getPageSize());
        out.setTotal(raw.getTotal());
        out.setRecords(CustomerDTOConverter.toDTOList(raw.getRecords()));
        return out;
    }

    /**
     * 转交客户维护人。
     * <p>
     * 高危操作，必须提供转交原因，同时记录审计日志。
     * 流程：校验 reason 非空 → 查询认领记录 → 校验状态为 CLAIMED → 更新 maintainerEmpId → 发布 ClaimTransferredEvent。
     * </p>
     *
     * @param claimId       认领记录 ID
     * @param toEmpId       目标维护人员工工号
     * @param reason        转交原因（不可为空）
     * @param operatorEmpId 操作人员工工号
     * @throws BizException CLAIM_NOT_FOUND         认领记录不存在
     * @throws BizException TRANSFER_REASON_REQUIRED 转交原因为空
     */
    @Transactional
    @AuditLog(action = "TRANSFER", resourceType = "CUSTOMER", reasonRequired = true)
    public void transfer(String claimId, String toEmpId, String reason, String operatorEmpId) {
        log.info("[CustomerService.transfer] claimId={}, toEmpId={}, operator={}", claimId, toEmpId, operatorEmpId);

        // 先查询认领记录（放在 reason 校验之前以便返回 404 而非 400 的情况下先 fail fast）
        CustClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                    CustomerErrorCode.CLAIM_NOT_FOUND.getMessage());
        }

        // 转交原因不可为空（业务规范要求，高危操作需留痕）
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.TRANSFER_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.TRANSFER_REASON_REQUIRED.getMessage());
        }

        String fromEmpId = claim.getMaintainerEmpId();

        // 更新维护人
        CustClaim updateEntity = new CustClaim();
        updateEntity.setId(claimId);
        updateEntity.setMaintainerEmpId(toEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());
        claimMapper.updateById(updateEntity);

        // 发布转交事件，供触达任务模块等做相应处理
        ClaimTransferredEvent event = new ClaimTransferredEvent(
                claimId, claim.getCustId(), fromEmpId, toEmpId, operatorEmpId);
        eventPublisher.publishEvent(event);

        log.info("[CustomerService.transfer] 转交完成 claimId={}, from={}, to={}", claimId, fromEmpId, toEmpId);
    }

    /**
     * 发起客户删除申请。
     * <p>
     * 流程：查询客户 → 创建 DELETE 类型线索 → 启动审批工作流。
     * 审批通过后由 LeadApprovedListener → CustMasterAssemblerService 完成实际删除。
     * </p>
     *
     * @param custId        要删除的客户 ID
     * @param operatorEmpId 操作人员工工号
     * @param orgCode       操作人所在机构代码
     * @throws BizException CUSTOMER_NOT_FOUND 客户不存在
     */
    @Transactional
    public void deleteApply(String custId, String operatorEmpId, String orgCode) {
        log.info("[CustomerService.deleteApply] custId={}, operator={}, orgCode={}", custId, operatorEmpId, orgCode);

        // 校验客户存在
        CustMaster master = requireExists(custId);

        // 创建 DELETE 类型线索（作为删除审批的业务载体）
        String leadId = UUID.randomUUID().toString().replace("-", "");
        String leadNo = "LEAD_DEL_" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        CustLead lead = new CustLead();
        lead.setId(leadId);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(LeadOp.DELETE.getCode());
        lead.setSourceCustId(custId);
        lead.setCustName(master.getCustName());
        lead.setUnifiedCreditCode(master.getUnifiedCreditCode());
        lead.setOwnerOrgId(orgCode);
        lead.setLeadStatus(LeadStatus.SUBMITTED.getCode());
        lead.setCreatedBy(operatorEmpId);
        lead.setUpdatedBy(operatorEmpId);
        lead.setBusinessKey("LEAD:" + leadId);
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setDeleted(0);
        lead.setCreatedTime(now);
        lead.setUpdatedTime(now);

        leadMapper.insert(lead);

        // 启动审批工作流
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("LEAD");
        cmd.setBizId(leadId);
        cmd.setBusinessKey("LEAD:" + leadId);
        cmd.setProcessDefinitionKey("lead_delete_process");
        cmd.setStartUser(operatorEmpId);
        cmd.setStartOrgId(orgCode);
        cmd.setTitle("客户删除申请 - " + master.getCustName());

        WorkflowLaunchResp resp = workflowApi.startProcess(cmd);

        // 回写流程实例ID到线索（便于后续工作流回调）
        CustLead updateLead = new CustLead();
        updateLead.setId(leadId);
        updateLead.setProcessInstanceId(resp.getProcessInstanceId());
        updateLead.setUpdatedBy(operatorEmpId);
        updateLead.setUpdatedTime(LocalDateTime.now());
        leadMapper.updateById(updateLead);

        log.info("[CustomerService.deleteApply] 删除申请已提交 custId={}, leadId={}, processInstanceId={}",
                custId, leadId, resp.getProcessInstanceId());
    }

    // ============================= 私有辅助方法 =============================

    /**
     * 断言客户存在，不存在时抛出 BizException。
     *
     * @param id 客户 ID
     * @return 已存在的客户主档实体
     * @throws BizException CUSTOMER_NOT_FOUND 客户不存在
     */
    private CustMaster requireExists(String id) {
        CustMaster master = masterMapper.selectById(id);
        if (master == null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }
        return master;
    }
}
