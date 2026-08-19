package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.LeadImportBatchMapper;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 线索管理业务服务。
 * <p>
 * 负责线索的创建草稿、编辑、删除、提交审批及分页查询。
 * 状态机流转：DRAFT → SUBMITTED → IN_APPROVAL → APPROVED / REJECTED。
 * 提交审批时通过 {@link WorkflowApi} 启动流程，业务键格式为 {@code LEAD:{leadId}}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final CustLeadMapper leadMapper;
    private final LeadImportBatchMapper batchMapper;
    private final WorkflowApi workflowApi;
    private final ApplicationEventPublisher eventPublisher;
    private final BizScopeApi bizScopeApi;

    private static final String PROCESS_DEFINITION_KEY = "lead_approve_v1";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 新建线索草稿。
     * <p>
     * 生成 UUID 主键和唯一线索编号，初始状态 DRAFT，操作类型 CREATE，版本号 1，is_latest=1。
     * </p>
     *
     * @param custName              客户名称
     * @param unifiedCreditCode     统一社会信用代码
     * @param contactPerson         联系人姓名
     * @param contactMobile         联系人手机号
     * @param industry              行业分类
     * @param groupType             集团类型
     * @param customerType          客户类型
     * @param isKeystone            是否重点客户
     * @param enterpriseType        企业类型
     * @param groupName             集团名称
     * @param isAccountOpened       是否已开户
     * @param customerDesc          客户描述
     * @param creditAmount          授信金额
     * @param creditExposureAmount  授信敞口金额
     * @param leadSource            线索来源
     * @param tagIds                标签ID列表（JSON）
     * @param remark                备注
     * @param operatorEmpId         操作人员工工号
     * @param orgCode               归属机构代码
     * @return 新建的线索实体
     */
    @Transactional
    public CustLead createDraft(
            String custName, String unifiedCreditCode,
            String contactPerson, String contactMobile,
            String industry, String groupType, String customerType,
            Integer isKeystone, String enterpriseType, String groupName,
            Integer isAccountOpened, String customerDesc,
            BigDecimal creditAmount, BigDecimal creditExposureAmount,
            String leadSource, String tagIds, String remark,
            String operatorEmpId, String orgCode) {

        log.info("[LeadService.createDraft] custName={}, operator={}, org={}", custName, operatorEmpId, orgCode);

        String id = UUID.randomUUID().toString().replace("-", "");
        String leadNo = generateLeadNo();
        LocalDateTime now = LocalDateTime.now();

        CustLead entity = new CustLead();
        entity.setId(id);
        entity.setLeadNo(leadNo);
        entity.setLeadOp(LeadOp.CREATE.getCode());
        entity.setLeadStatus(LeadStatus.DRAFT.getCode());
        entity.setVersionNo(1);
        entity.setIsLatest(1);
        entity.setCustName(custName);
        entity.setUnifiedCreditCode(unifiedCreditCode);
        entity.setContactPerson(contactPerson);
        entity.setContactMobile(contactMobile);
        entity.setIndustry(industry);
        entity.setGroupType(groupType);
        entity.setCustomerType(customerType);
        entity.setIsKeystone(isKeystone);
        entity.setEnterpriseType(enterpriseType);
        entity.setGroupName(groupName);
        entity.setIsAccountOpened(isAccountOpened);
        entity.setCustomerDesc(customerDesc);
        entity.setCreditAmount(creditAmount);
        entity.setCreditExposureAmount(creditExposureAmount);
        entity.setLeadSource(leadSource);
        entity.setTagIds(tagIds);
        entity.setRemark(remark);
        entity.setOwnerOrgId(orgCode);
        entity.setCreatedBy(operatorEmpId);
        entity.setCreatedTime(now);
        entity.setUpdatedBy(operatorEmpId);
        entity.setUpdatedTime(now);
        entity.setDeleted(0);

        leadMapper.insert(entity);
        log.info("[LeadService.createDraft] created leadId={}, leadNo={}", id, leadNo);
        return entity;
    }

    /**
     * 编辑线索草稿。
     * <p>
     * 只允许 DRAFT 状态的线索被编辑，否则抛出 {@link BizException}。
     * 更新非 null 字段，不覆盖为 null 的字段。
     * </p>
     *
     * @param id                    线索ID
     * @param custName              客户名称
     * @param unifiedCreditCode     统一社会信用代码
     * @param contactPerson         联系人姓名
     * @param contactMobile         联系人手机号
     * @param industry              行业分类
     * @param groupType             集团类型
     * @param customerType          客户类型
     * @param isKeystone            是否重点客户
     * @param enterpriseType        企业类型
     * @param groupName             集团名称
     * @param isAccountOpened       是否已开户
     * @param customerDesc          客户描述
     * @param creditAmount          授信金额
     * @param creditExposureAmount  授信敞口金额
     * @param leadSource            线索来源
     * @param tagIds                标签ID列表（JSON）
     * @param remark                备注
     * @param operatorEmpId         操作人员工工号
     * @return 更新后的线索实体
     */
    @Transactional
    public CustLead updateDraft(
            String id,
            String custName, String unifiedCreditCode,
            String contactPerson, String contactMobile,
            String industry, String groupType, String customerType,
            Integer isKeystone, String enterpriseType, String groupName,
            Integer isAccountOpened, String customerDesc,
            BigDecimal creditAmount, BigDecimal creditExposureAmount,
            String leadSource, String tagIds, String remark,
            String operatorEmpId) {

        log.info("[LeadService.updateDraft] id={}, operator={}", id, operatorEmpId);

        CustLead existing = requireExistsAndDraft(id);
        assertWritePermission(existing, operatorEmpId);

        // 只更新非 null 字段
        CustLead updateEntity = new CustLead();
        updateEntity.setId(id);
        updateEntity.setCustName(custName);
        updateEntity.setUnifiedCreditCode(unifiedCreditCode);
        updateEntity.setContactPerson(contactPerson);
        updateEntity.setContactMobile(contactMobile);
        updateEntity.setIndustry(industry);
        updateEntity.setGroupType(groupType);
        updateEntity.setCustomerType(customerType);
        updateEntity.setIsKeystone(isKeystone);
        updateEntity.setEnterpriseType(enterpriseType);
        updateEntity.setGroupName(groupName);
        updateEntity.setIsAccountOpened(isAccountOpened);
        updateEntity.setCustomerDesc(customerDesc);
        updateEntity.setCreditAmount(creditAmount);
        updateEntity.setCreditExposureAmount(creditExposureAmount);
        updateEntity.setLeadSource(leadSource);
        updateEntity.setTagIds(tagIds);
        updateEntity.setRemark(remark);
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        leadMapper.updateById(updateEntity);

        // 将更新合并到现有对象后返回
        mergeUpdate(existing, updateEntity);
        log.info("[LeadService.updateDraft] updated leadId={}", id);
        return existing;
    }

    /**
     * 删除线索草稿（逻辑删除）。
     * <p>
     * 只允许 DRAFT 状态线索被删除，设置 deleted=1。
     * </p>
     *
     * @param id            线索ID
     * @param operatorEmpId 操作人
     */
    @Transactional
    public void deleteDraft(String id, String operatorEmpId) {
        log.info("[LeadService.deleteDraft] id={}, operator={}", id, operatorEmpId);

        CustLead existing = requireExistsAndDraft(id);
        assertWritePermission(existing, operatorEmpId);

        CustLead updateEntity = new CustLead();
        updateEntity.setId(id);
        updateEntity.setDeleted(1);
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        leadMapper.updateById(updateEntity);
        log.info("[LeadService.deleteDraft] soft-deleted leadId={}", id);
    }

    /**
     * 提交线索审批。
     * <p>
     * 使用 SELECT FOR UPDATE 加行锁防止并发提交，状态必须为 DRAFT。
     * 流程：DRAFT → SUBMITTED（更新数据库）→ 启动 Flowable 流程 → IN_APPROVAL（更新数据库）。
     * businessKey 格式：{@code LEAD:{leadId}}。
     * </p>
     *
     * @param id            线索ID
     * @param operatorEmpId 操作人员工工号
     * @param orgCode       归属机构代码
     */
    @Transactional
    public void submitForApproval(String id, String operatorEmpId, String orgCode) {
        log.info("[LeadService.submitForApproval] id={}, operator={}", id, operatorEmpId);

        // SELECT FOR UPDATE 加行锁，防止并发提交
        CustLead lead = leadMapper.selectForUpdate(id);
        if (lead == null) {
            throw new BizException(CustomerErrorCode.LEAD_NOT_FOUND.getCode(),
                    CustomerErrorCode.LEAD_NOT_FOUND.getMessage());
        }
        if (!LeadStatus.DRAFT.getCode().equals(lead.getLeadStatus())) {
            throw new BizException(CustomerErrorCode.LEAD_NOT_SUBMITTABLE.getCode(),
                    CustomerErrorCode.LEAD_NOT_SUBMITTABLE.getMessage());
        }
        assertWritePermission(lead, operatorEmpId);

        // 先更新为 SUBMITTED 状态
        leadMapper.updateStatusById(id, LeadStatus.SUBMITTED.getCode(), operatorEmpId);

        // 启动工作流
        String businessKey = "LEAD:" + id;
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("LEAD");
        cmd.setBizId(id);
        cmd.setBusinessKey(businessKey);
        cmd.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        cmd.setStartUser(operatorEmpId);
        cmd.setStartOrgId(orgCode);
        cmd.setTitle("线索审批-" + lead.getCustName());

        WorkflowLaunchResp launchResp = workflowApi.startProcess(cmd);

        // 更新流程实例ID和状态为 IN_APPROVAL
        CustLead updateEntity = new CustLead();
        updateEntity.setId(id);
        updateEntity.setProcessInstanceId(launchResp.getProcessInstanceId());
        updateEntity.setBusinessKey(businessKey);
        updateEntity.setSubmittedBy(operatorEmpId);
        updateEntity.setSubmittedTime(LocalDateTime.now());
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());
        leadMapper.updateById(updateEntity);

        leadMapper.updateStatusById(id, LeadStatus.IN_APPROVAL.getCode(), operatorEmpId);

        log.info("[LeadService.submitForApproval] submitted leadId={}, processInstanceId={}",
                id, launchResp.getProcessInstanceId());
    }

    /**
     * 按 ID 查询线索，不存在时抛出业务异常。
     *
     * @param id 线索ID
     * @return 线索实体
     */
    public CustLead getById(String id) {
        log.debug("[LeadService.getById] id={}", id);
        CustLead lead = leadMapper.selectById(id);
        if (lead == null) {
            throw new BizException(CustomerErrorCode.LEAD_NOT_FOUND.getCode(),
                    CustomerErrorCode.LEAD_NOT_FOUND.getMessage());
        }
        return lead;
    }

    /**
     * 分页查询线索列表（只查最新版本 is_latest=1）。
     * <p>
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword    关键词（模糊搜索客户名称和统一信用代码）
     * @param status     线索状态过滤
     * @param ownerOrgId 归属机构代码
     * @param pageNo     页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 分页结果
     */
    public PageResult<CustLead> listPage(String keyword, String status, String ownerOrgId,
                                         int pageNo, int pageSize) {
        log.debug("[LeadService.listPage] keyword={}, status={}, ownerOrgId={}, pageNo={}, pageSize={}",
                keyword, status, ownerOrgId, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<CustLead> records = leadMapper.selectPage(keyword, status, ownerOrgId, offset, pageSize);
        long total = leadMapper.countPage(keyword, status, ownerOrgId);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    // ============================= 私有辅助方法 =============================

    /**
     * 断言线索存在且为草稿状态。
     *
     * @param id 线索ID
     * @return 草稿状态的线索实体
     */
    private CustLead requireExistsAndDraft(String id) {
        CustLead lead = leadMapper.selectById(id);
        if (lead == null) {
            throw new BizException(CustomerErrorCode.LEAD_NOT_FOUND.getCode(),
                    CustomerErrorCode.LEAD_NOT_FOUND.getMessage());
        }
        if (!LeadStatus.DRAFT.getCode().equals(lead.getLeadStatus())) {
            // P1C 2026-04-29 升级：原 CUST-40003 LEAD_NOT_DRAFT 重命名为 CUST-40301 LEAD_EDIT_FORBIDDEN
            // （语义升级 409→403：操作者无权对非草稿线索执行编辑）
            throw new BizException(CustomerErrorCode.LEAD_EDIT_FORBIDDEN.getCode(),
                    CustomerErrorCode.LEAD_EDIT_FORBIDDEN.getMessage());
        }
        return lead;
    }

    /** 写操作必须同时满足 LEAD 业务数据范围。 */
    private void assertWritePermission(CustLead lead, String operatorEmpId) {
        if (!bizScopeApi.checkWritePermission(operatorEmpId, BizType.LEAD,
                lead.getOwnerOrgId(), lead.getCreatedBy())) {
            throw new BizException(CustomerErrorCode.LEAD_WRITE_FORBIDDEN.getCode(),
                    CustomerErrorCode.LEAD_WRITE_FORBIDDEN.getMessage());
        }
    }

    /**
     * 生成唯一线索编号，格式：LEAD_{yyyyMMdd}_{4位随机数}。
     *
     * @return 线索编号
     */
    private String generateLeadNo() {
        String datePart = LocalDateTime.now().format(DATE_FMT);
        // 4位随机数，不足补零
        int rand = new Random().nextInt(10000);
        return String.format("LEAD_%s_%04d", datePart, rand);
    }

    /**
     * 将 updateEntity 中的非 null 字段合并到 existing 中。
     *
     * @param existing     原实体
     * @param updateEntity 更新实体
     */
    private void mergeUpdate(CustLead existing, CustLead updateEntity) {
        if (updateEntity.getCustName() != null) existing.setCustName(updateEntity.getCustName());
        if (updateEntity.getUnifiedCreditCode() != null) existing.setUnifiedCreditCode(updateEntity.getUnifiedCreditCode());
        if (updateEntity.getContactPerson() != null) existing.setContactPerson(updateEntity.getContactPerson());
        if (updateEntity.getContactMobile() != null) existing.setContactMobile(updateEntity.getContactMobile());
        if (updateEntity.getIndustry() != null) existing.setIndustry(updateEntity.getIndustry());
        if (updateEntity.getGroupType() != null) existing.setGroupType(updateEntity.getGroupType());
        if (updateEntity.getCustomerType() != null) existing.setCustomerType(updateEntity.getCustomerType());
        if (updateEntity.getIsKeystone() != null) existing.setIsKeystone(updateEntity.getIsKeystone());
        if (updateEntity.getEnterpriseType() != null) existing.setEnterpriseType(updateEntity.getEnterpriseType());
        if (updateEntity.getGroupName() != null) existing.setGroupName(updateEntity.getGroupName());
        if (updateEntity.getIsAccountOpened() != null) existing.setIsAccountOpened(updateEntity.getIsAccountOpened());
        if (updateEntity.getCustomerDesc() != null) existing.setCustomerDesc(updateEntity.getCustomerDesc());
        if (updateEntity.getCreditAmount() != null) existing.setCreditAmount(updateEntity.getCreditAmount());
        if (updateEntity.getCreditExposureAmount() != null) existing.setCreditExposureAmount(updateEntity.getCreditExposureAmount());
        if (updateEntity.getLeadSource() != null) existing.setLeadSource(updateEntity.getLeadSource());
        if (updateEntity.getTagIds() != null) existing.setTagIds(updateEntity.getTagIds());
        if (updateEntity.getRemark() != null) existing.setRemark(updateEntity.getRemark());
        existing.setUpdatedBy(updateEntity.getUpdatedBy());
        existing.setUpdatedTime(updateEntity.getUpdatedTime());
    }
}
