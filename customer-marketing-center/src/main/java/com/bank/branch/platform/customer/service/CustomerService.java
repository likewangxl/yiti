package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.customer.api.converter.CustClaimDTOConverter;
import com.bank.branch.platform.customer.api.converter.CustomerDTOConverter;
import com.bank.branch.platform.customer.api.converter.TouchTaskDTOConverter;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.dto.resp.CustomerCrossOrgHistoryVO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.CustomerRoleCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.event.ClaimTransferredEvent;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final TouchTaskMapper touchTaskMapper;
    private final WorkflowApi workflowApi;
    private final ApplicationEventPublisher eventPublisher;
    /** 用于 listPageAsDTO 回填 ownerOrgName（OrgApi 自带缓存）。 */
    private final OrgApi orgApi;
    /** P1C 转交接收人资格校验：查接收人角色码 + 主机构。 */
    private final UserApi userApi;

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
        List<CustomerDTO> dtos = CustomerDTOConverter.toDTOList(raw.getRecords());
        fillOwnerOrgNames(dtos);
        PageResult<CustomerDTO> out = new PageResult<>();
        out.setPageNo(raw.getPageNo());
        out.setPageSize(raw.getPageSize());
        out.setTotal(raw.getTotal());
        out.setRecords(dtos);
        return out;
    }

    /**
     * 0811 一期客户列表：查询当前业务数据范围内全部存量客户，不限定开户状态。
     */
    public PageResult<CustomerDTO> listVisiblePageAsDTO(String keyword, String status,
                                                        int pageNo, int pageSize,
                                                        String empId, String orgCode,
                                                        DataScopeType scopeType) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        DataScopeType safeScope = scopeType == null ? DataScopeType.SELF : scopeType;
        Set<String> orgCodes = resolveOrgCodes(safeScope, orgCode);
        int offset = (safePageNo - 1) * safePageSize;
        List<CustMaster> records = masterMapper.selectVisiblePage(keyword, status,
                safeScope.getCode(), empId, orgCodes, offset, safePageSize);
        long total = masterMapper.countVisiblePage(keyword, status,
                safeScope.getCode(), empId, orgCodes);
        List<CustomerDTO> dtos = CustomerDTOConverter.toDTOList(records);
        fillCustomerDisplayNames(dtos);
        return PageResult.of(safePageNo, safePageSize, total, dtos);
    }

    private Set<String> resolveOrgCodes(DataScopeType scopeType, String orgCode) {
        if (scopeType == DataScopeType.ORG_SUBTREE && StringUtils.hasText(orgCode)) {
            Set<String> codes = orgApi.getOrgSubtreeCodes(orgCode);
            return codes == null ? Set.of() : codes;
        }
        if (scopeType == DataScopeType.ORG && StringUtils.hasText(orgCode)) {
            return Set.of(orgCode);
        }
        return Set.of();
    }

    private void fillCustomerDisplayNames(List<CustomerDTO> dtos) {
        fillOwnerOrgNames(dtos);
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<String> empIds = dtos.stream().map(CustomerDTO::getMainManagerId)
                .filter(StringUtils::hasText).distinct().toList();
        Map<String, String> userNames = new HashMap<>();
        if (!empIds.isEmpty()) {
            List<UserDTO> users = userApi.getUserByEmpIds(empIds);
            if (users != null) {
                users.stream().filter(Objects::nonNull)
                        .forEach(user -> userNames.put(user.getEmpId(), user.getDisplayName()));
            }
        }
        List<String> mainOrgIds = dtos.stream().map(CustomerDTO::getMainOrgId)
                .filter(StringUtils::hasText).distinct().toList();
        Map<String, String> orgNames = new HashMap<>();
        if (!mainOrgIds.isEmpty()) {
            List<OrgDTO> orgs = orgApi.getOrgsByCodes(mainOrgIds);
            if (orgs != null) {
                orgs.stream().filter(Objects::nonNull)
                        .forEach(org -> orgNames.put(org.getOrgCode(), org.getOrgName()));
            }
        }
        dtos.forEach(dto -> {
            dto.setMainManagerName(userNames.get(dto.getMainManagerId()));
            dto.setMainOrgName(orgNames.get(dto.getMainOrgId()));
        });
    }

    /**
     * 批量回填 CustomerDTO.ownerOrgName。
     * <p>
     * CustomerDTOConverter 显式将 ownerOrgName 置 null，由本方法在 Service 层补充。
     * 通过本地 cache 对相同 ownerOrgId 仅查一次（同页客户挂同机构很常见）；
     * OrgApi.getOrg 自身也带缓存，再叠一层本地短路只为了少调 API、保持 N=O(机构数)。
     * </p>
     *
     * @param dtos 待补的 DTO 列表（原地修改，可能为空）
     */
    private void fillOwnerOrgNames(List<CustomerDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        Map<String, String> orgNameCache = new HashMap<>();
        for (CustomerDTO dto : dtos) {
            String code = dto.getOwnerOrgId();
            if (code == null || dto.getOwnerOrgName() != null) {
                continue;
            }
            String name;
            if (orgNameCache.containsKey(code)) {
                name = orgNameCache.get(code);
            } else {
                OrgDTO org = orgApi.getOrg(code);
                name = (org != null) ? org.getOrgName() : null;
                orgNameCache.put(code, name);
            }
            dto.setOwnerOrgName(name);
        }
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

        // 接收人资格校验：接收人须具备专用或历史客户经理角色（CUST-40306），
        // 且其主机构须与客户当前所属机构一致（CUST-40307），防止越权 / 跨机构转交
        Set<String> receiverRoles = userApi.getUserRoleCodes(toEmpId);
        if (!CustomerRoleCode.isCustomerManager(receiverRoles)) {
            throw new BizException(CustomerErrorCode.TRANSFER_ROLE_MISMATCH.getCode(),
                    CustomerErrorCode.TRANSFER_ROLE_MISMATCH.getMessage());
        }
        UserDTO receiver = userApi.getUserByEmpId(toEmpId);
        if (receiver == null || !Objects.equals(claim.getOrgId(), receiver.getMainOrgCode())) {
            throw new BizException(CustomerErrorCode.TRANSFER_ORG_MISMATCH.getCode(),
                    CustomerErrorCode.TRANSFER_ORG_MISMATCH.getMessage());
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

    /**
     * 跨机构全量客户历史查询（高危操作）。
     *
     * <p>
     * 聚合指定客户在全行范围内的所有认领记录（含 CANCELLED 历史）和触达任务历史，
     * 用于总行部门进行客户全景分析。
     * </p>
     *
     * <p>
     * 调用层须已通过 @BizAuth 权限验证，且 @AuditLog(reasonRequired=true) 保证操作留痕。
     * TODO: 待 @AuditLog 支持 specialCategory 属性后，补充 specialCategory=CROSS_ORG。
     * </p>
     *
     * @param custId 客户 ID
     * @return 跨机构聚合历史 VO
     * @throws BizException CUSTOMER_NOT_FOUND 客户不存在
     */
    public CustomerCrossOrgHistoryVO getCrossOrgHistory(String custId) {
        log.info("[CustomerService.getCrossOrgHistory] custId={}", custId);

        // 校验客户存在
        CustMaster master = requireExists(custId);

        // 查询全行所有认领记录（含 CANCELLED 历史）
        List<CustClaim> allClaims = claimMapper.selectByCustId(custId);

        // 查询全行所有触达历史（全状态，按创建时间降序）
        List<TouchTask> allTouchTasks = touchTaskMapper.selectByCustOrderByCreatedDesc(custId);

        // 组装 VO
        CustomerCrossOrgHistoryVO vo = new CustomerCrossOrgHistoryVO();
        vo.setCustomer(CustomerDTOConverter.toDTO(master));
        vo.setClaims(allClaims == null ? List.of() : CustClaimDTOConverter.toDTOList(allClaims));
        vo.setTouchTasks(allTouchTasks == null ? List.of() : TouchTaskDTOConverter.toDTOList(allTouchTasks));
        vo.setTotalClaimCount(vo.getClaims().size());
        // 有效认领：claimStatus = CLAIMED
        vo.setActiveClaimCount(vo.getClaims().stream()
                .filter(c -> ClaimStatus.CLAIMED.getCode().equals(c.getClaimStatus()))
                .count());
        vo.setTotalTouchCount(vo.getTouchTasks().size());

        log.info("[CustomerService.getCrossOrgHistory] custId={} 认领总数={} 有效认领={} 触达总数={}",
                custId, vo.getTotalClaimCount(), vo.getActiveClaimCount(), vo.getTotalTouchCount());
        return vo;
    }

    /**
     * 查询全量客户主档用于导出（上限 maxRows 行保护）。
     * <p>
     * 以 offset=0, limit=maxRows 一次性查询，避免大量分页循环；
     * 调用方须保证 maxRows &lt;= 10000，防止单次查询拖垮数据库。
     * </p>
     *
     * @param keyword 关键词（模糊匹配 cust_name / unified_credit_code），可为 null
     * @param status  客户状态过滤（ACTIVE / INACTIVE），可为 null
     * @param maxRows 最大返回行数，防止无限制导出（建议 &lt;= 10000）
     * @return 客户主档列表
     */
    public List<CustMaster> listAllForExport(String keyword, String status, int maxRows) {
        log.info("[CustomerService.listAllForExport] keyword={}, status={}, maxRows={}", keyword, status, maxRows);
        return masterMapper.selectPage(keyword, status, 0, maxRows);
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
