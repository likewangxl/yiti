package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * 线索版本管理服务。
 * <p>
 * 负责对已审批通过的客户主档创建修改版本和删除版本线索。
 * 修改版本（leadOp=UPDATE）：复制客户信息，允许后续编辑后提交审批。
 * 删除版本（leadOp=DELETE）：标记客户待删除，提交审批后执行删除。
 * 每次创建新版本时，将前一个最新版本的 isLatest 设为 0，新版本 isLatest=1。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadVersionService {

    private final CustLeadMapper leadMapper;
    private final CustMasterMapper masterMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 为已存在的客户主档创建修改版本线索。
     * <p>
     * 步骤：
     * 1. 查询 sourceCustId 对应的 CUSTOMER_MARKET_CUSTOMER（不存在则抛异常）
     * 2. 查询该客户最新版本线索
     * 3. 创建新 CustLead，leadOp=UPDATE，复制客户信息，versionNo=prev.versionNo+1，isLatest=1
     * 4. 将旧版本 isLatest 设为 0
     * 5. 返回新线索
     * </p>
     *
     * @param sourceCustId         源客户ID（CUSTOMER_MARKET_CUSTOMER.id）
     * @param custName             客户名称（可覆盖原值）
     * @param unifiedCreditCode    统一社会信用代码
     * @param contactPerson        联系人姓名
     * @param contactMobile        联系人手机号
     * @param industry             行业分类
     * @param groupType            集团类型
     * @param customerType         客户类型
     * @param isKeystone           是否重点客户
     * @param enterpriseType       企业类型
     * @param groupName            集团名称
     * @param isAccountOpened      是否已开户
     * @param customerDesc         客户描述
     * @param creditAmount         授信金额
     * @param creditExposureAmount 授信敞口金额
     * @param leadSource           线索来源
     * @param tagIds               标签ID列表
     * @param assignedTo           指派人
     * @param remark               备注
     * @param operatorEmpId        操作人员工工号
     * @param orgCode              归属机构代码
     * @return 新创建的修改版本线索
     */
    @Transactional
    public CustLead createEditVersion(
            String sourceCustId,
            String custName, String unifiedCreditCode,
            String contactPerson, String contactMobile,
            String industry, String groupType, String customerType,
            Integer isKeystone, String enterpriseType, String groupName,
            Integer isAccountOpened, String customerDesc,
            BigDecimal creditAmount, BigDecimal creditExposureAmount,
            String leadSource, String tagIds, String assignedTo, String remark,
            String operatorEmpId, String orgCode) {

        log.info("[LeadVersionService.createEditVersion] sourceCustId={}, operator={}", sourceCustId, operatorEmpId);

        // 1. 查询客户主档，确认客户存在
        CustMaster master = masterMapper.selectById(sourceCustId);
        if (master == null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }

        // 2. 查询该客户最新版本线索
        CustLead prevLead = leadMapper.selectLatestBySourceCustId(sourceCustId);
        int newVersionNo = (prevLead != null) ? prevLead.getVersionNo() + 1 : 1;
        String prevLeadId = (prevLead != null) ? prevLead.getId() : null;

        // 3. 创建新版本线索（以客户主档信息为基础，覆盖传入的字段）
        CustLead newLead = buildNewVersion(
                sourceCustId, prevLeadId, LeadOp.UPDATE.getCode(), newVersionNo,
                master, custName, unifiedCreditCode, contactPerson, contactMobile,
                industry, groupType, customerType, isKeystone, enterpriseType, groupName,
                isAccountOpened, customerDesc, creditAmount, creditExposureAmount,
                leadSource, tagIds, assignedTo, remark, operatorEmpId, orgCode
        );

        // 4. 将旧版本 isLatest 置为 0
        if (prevLead != null) {
            CustLead updateOld = new CustLead();
            updateOld.setId(prevLead.getId());
            updateOld.setIsLatest(0);
            updateOld.setUpdatedBy(operatorEmpId);
            updateOld.setUpdatedTime(LocalDateTime.now());
            leadMapper.updateById(updateOld);
        }

        // 5. 插入新版本
        leadMapper.insert(newLead);
        log.info("[LeadVersionService.createEditVersion] created new version leadId={}, versionNo={}",
                newLead.getId(), newVersionNo);
        return newLead;
    }

    /**
     * 为已存在的客户主档创建删除版本线索。
     * <p>
     * 与 createEditVersion 类似，但 leadOp=DELETE，无需传入客户信息（复制主档数据）。
     * </p>
     *
     * @param sourceCustId  源客户ID
     * @param operatorEmpId 操作人员工工号
     * @param orgCode       归属机构代码
     * @return 新创建的删除版本线索
     */
    @Transactional
    public CustLead createDeleteVersion(String sourceCustId, String operatorEmpId, String orgCode) {
        log.info("[LeadVersionService.createDeleteVersion] sourceCustId={}, operator={}", sourceCustId, operatorEmpId);

        // 1. 查询客户主档，确认客户存在
        CustMaster master = masterMapper.selectById(sourceCustId);
        if (master == null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }

        // 2. 查询该客户最新版本线索
        CustLead prevLead = leadMapper.selectLatestBySourceCustId(sourceCustId);
        int newVersionNo = (prevLead != null) ? prevLead.getVersionNo() + 1 : 1;
        String prevLeadId = (prevLead != null) ? prevLead.getId() : null;

        // 3. 创建删除版本线索（完全复制主档数据，不允许修改）
        CustLead newLead = buildNewVersion(
                sourceCustId, prevLeadId, LeadOp.DELETE.getCode(), newVersionNo,
                master, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, operatorEmpId, orgCode
        );

        // 4. 将旧版本 isLatest 置为 0
        if (prevLead != null) {
            CustLead updateOld = new CustLead();
            updateOld.setId(prevLead.getId());
            updateOld.setIsLatest(0);
            updateOld.setUpdatedBy(operatorEmpId);
            updateOld.setUpdatedTime(LocalDateTime.now());
            leadMapper.updateById(updateOld);
        }

        // 5. 插入新版本
        leadMapper.insert(newLead);
        log.info("[LeadVersionService.createDeleteVersion] created delete version leadId={}, versionNo={}",
                newLead.getId(), newVersionNo);
        return newLead;
    }

    // ============================= 私有辅助方法 =============================

    /**
     * 构建新版本线索实体。
     * <p>
     * 以客户主档信息为基础，传入的字段非 null 时覆盖主档字段。
     * </p>
     */
    private CustLead buildNewVersion(
            String sourceCustId, String prevLeadId, String leadOp, int versionNo,
            CustMaster master,
            String custName, String unifiedCreditCode,
            String contactPerson, String contactMobile,
            String industry, String groupType, String customerType,
            Integer isKeystone, String enterpriseType, String groupName,
            Integer isAccountOpened, String customerDesc,
            BigDecimal creditAmount, BigDecimal creditExposureAmount,
            String leadSource, String tagIds, String assignedTo, String remark,
            String operatorEmpId, String orgCode) {

        String id = UUID.randomUUID().toString().replace("-", "");
        String leadNo = generateLeadNo();
        LocalDateTime now = LocalDateTime.now();

        CustLead lead = new CustLead();
        lead.setId(id);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(leadOp);
        lead.setSourceCustId(sourceCustId);
        lead.setPrevLeadId(prevLeadId);
        lead.setVersionNo(versionNo);
        lead.setIsLatest(1);
        lead.setLeadStatus(LeadStatus.DRAFT.getCode());

        // 以主档信息为基础，传入字段非 null 时覆盖
        lead.setCustName(custName != null ? custName : master.getCustName());
        lead.setUnifiedCreditCode(unifiedCreditCode != null ? unifiedCreditCode : master.getUnifiedCreditCode());
        lead.setContactPerson(contactPerson != null ? contactPerson : master.getContactPerson());
        lead.setContactMobile(contactMobile != null ? contactMobile : master.getContactMobile());
        lead.setIndustry(industry != null ? industry : master.getIndustry());
        lead.setGroupType(groupType != null ? groupType : master.getGroupType());
        lead.setCustomerType(customerType != null ? customerType : master.getCustomerType());
        lead.setIsKeystone(isKeystone != null ? isKeystone : master.getIsKeystone());
        lead.setEnterpriseType(enterpriseType != null ? enterpriseType : master.getEnterpriseType());
        lead.setGroupName(groupName != null ? groupName : master.getGroupName());
        lead.setIsAccountOpened(isAccountOpened != null ? isAccountOpened : master.getIsAccountOpened());
        lead.setCustomerDesc(customerDesc != null ? customerDesc : master.getCustomerDesc());
        lead.setCreditAmount(creditAmount != null ? creditAmount : master.getCreditAmount());
        lead.setCreditExposureAmount(creditExposureAmount != null ? creditExposureAmount : master.getCreditExposureAmount());
        lead.setLeadSource(leadSource);
        lead.setTagIds(tagIds);
        lead.setAssignedTo(assignedTo);
        lead.setRemark(remark);

        lead.setOwnerOrgId(orgCode);
        lead.setCreatedBy(operatorEmpId);
        lead.setCreatedTime(now);
        lead.setUpdatedBy(operatorEmpId);
        lead.setUpdatedTime(now);
        lead.setDeleted(0);

        return lead;
    }

    /**
     * 生成唯一线索编号，格式：LEAD_{yyyyMMdd}_{4位随机数}。
     *
     * @return 线索编号
     */
    private String generateLeadNo() {
        String datePart = LocalDateTime.now().format(DATE_FMT);
        int rand = new Random().nextInt(10000);
        return String.format("LEAD_%s_%04d", datePart, rand);
    }
}
