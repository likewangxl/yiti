package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.CustMasterStatus;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.event.CustomerDeletedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * 客户主档装配服务。
 * <p>
 * 根据审批通过的线索操作类型，将线索数据同步到客户主档表：
 * <ul>
 *   <li>CREATE：从线索字段生成新客户主档，生成 custNo，status=ACTIVE</li>
 *   <li>UPDATE：通过 sourceCustId 查找已有客户主档，更新全部业务字段</li>
 *   <li>DELETE：通过 sourceCustId 查找已有客户主档，标记 status=INACTIVE、deleted=1，并发布 CustomerDeletedEvent</li>
 * </ul>
 * 所有操作都在同一个事务内完成，由调用方（LeadCallbackReconcileService.reconcileApproved）在 reconcile 事务内同步调用（V1.11#1 起）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustMasterAssemblerService {

    private final CustMasterMapper masterMapper;
    private final CustLeadMapper leadMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 从审批通过的线索装配客户主档。
     * <p>
     * 根据 leadOp 分支处理：
     * <ul>
     *   <li>CREATE：插入新客户主档</li>
     *   <li>UPDATE：更新已有客户主档（通过 sourceCustId 定位）</li>
     *   <li>DELETE：逻辑删除客户主档并发布删除事件</li>
     * </ul>
     * </p>
     *
     * @param lead 完整的线索实体（包含所有业务字段）
     */
    @Transactional
    public void assembleFromLead(CustLead lead) {
        log.info("[CustMasterAssemblerService.assembleFromLead] leadId={}, leadOp={}, sourceCustId={}",
                lead.getId(), lead.getLeadOp(), lead.getSourceCustId());

        String leadOp = lead.getLeadOp();

        if (LeadOp.CREATE.getCode().equals(leadOp)) {
            handleCreate(lead);
        } else if (LeadOp.UPDATE.getCode().equals(leadOp)) {
            handleUpdate(lead);
        } else if (LeadOp.DELETE.getCode().equals(leadOp)) {
            handleDelete(lead);
        } else {
            log.warn("[CustMasterAssemblerService.assembleFromLead] 未知的 leadOp={}, leadId={}", leadOp, lead.getId());
        }
    }

    // ============================= 私有处理方法 =============================

    /**
     * 处理 CREATE 操作：从线索字段创建新客户主档。
     * <p>
     * custNo 格式为 CUST_{timestamp}_{random4}，保证在同一毫秒内创建的客户编号不重复。
     * </p>
     *
     * @param lead 线索实体
     */
    private void handleCreate(CustLead lead) {
        LocalDateTime now = LocalDateTime.now();
        String custNo = generateCustNo();
        String id = UUID.randomUUID().toString().replace("-", "");

        CustMaster master = new CustMaster();
        master.setId(id);
        master.setCustNo(custNo);
        copyLeadFieldsToMaster(lead, master);
        master.setLeadId(lead.getId());
        master.setOwnerOrgId(lead.getOwnerOrgId());
        master.setStatus(CustMasterStatus.ACTIVE.getCode());
        master.setDeleted(0);
        master.setCreatedTime(now);
        master.setUpdatedTime(now);

        masterMapper.insert(master);
        log.info("[CustMasterAssemblerService.handleCreate] 新建客户主档 custId={}, custNo={}, leadId={}",
                id, custNo, lead.getId());
    }

    /**
     * 处理 UPDATE 操作：通过 sourceCustId 找到已有客户主档并更新业务字段。
     *
     * @param lead 线索实体（lead.sourceCustId 指向要更新的客户主档 ID）
     */
    private void handleUpdate(CustLead lead) {
        String custId = lead.getSourceCustId();
        // 确保客户存在（若不存在则 selectById 返回 null，直接报 NPE 让上层感知异常）
        masterMapper.selectById(custId);

        CustMaster updateEntity = new CustMaster();
        updateEntity.setId(custId);
        copyLeadFieldsToMaster(lead, updateEntity);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        masterMapper.updateById(updateEntity);
        log.info("[CustMasterAssemblerService.handleUpdate] 更新客户主档 custId={}, leadId={}", custId, lead.getId());
    }

    /**
     * 处理 DELETE 操作：逻辑删除客户主档，发布 CustomerDeletedEvent。
     *
     * @param lead 线索实体（lead.sourceCustId 指向要删除的客户主档 ID）
     */
    private void handleDelete(CustLead lead) {
        String custId = lead.getSourceCustId();
        CustMaster existing = masterMapper.selectById(custId);

        CustMaster updateEntity = new CustMaster();
        updateEntity.setId(custId);
        updateEntity.setStatus(CustMasterStatus.INACTIVE.getCode());
        updateEntity.setDeleted(1);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        masterMapper.updateById(updateEntity);

        // 发布客户删除事件，供其他模块（触达、认领、标签关联）清理关联数据
        CustomerDeletedEvent event = new CustomerDeletedEvent(
                custId,
                existing != null ? existing.getCustNo() : null,
                lead.getCreatedBy()
        );
        eventPublisher.publishEvent(event);
        log.info("[CustMasterAssemblerService.handleDelete] 逻辑删除客户主档 custId={}, leadId={}", custId, lead.getId());
    }

    /**
     * 从线索实体中复制业务字段到客户主档实体。
     * <p>
     * 将两个实体之间公共的业务字段批量赋值，避免在各分支中重复。
     * 只复制业务字段，不复制 id / custNo / status / deleted / createdTime / updatedTime / leadId 等元字段。
     * </p>
     *
     * @param lead   线索实体（来源）
     * @param master 客户主档实体（目标，仅设置线索中有值的字段）
     */
    private void copyLeadFieldsToMaster(CustLead lead, CustMaster master) {
        master.setCustName(lead.getCustName());
        master.setUnifiedCreditCode(lead.getUnifiedCreditCode());
        master.setContactPerson(lead.getContactPerson());
        master.setContactMobile(lead.getContactMobile());
        master.setIndustry(lead.getIndustry());
        master.setGroupType(lead.getGroupType());
        master.setCustomerType(lead.getCustomerType());
        master.setIsKeystone(lead.getIsKeystone());
        master.setEnterpriseType(lead.getEnterpriseType());
        master.setGroupName(lead.getGroupName());
        master.setIsAccountOpened(lead.getIsAccountOpened());
        master.setCustomerDesc(lead.getCustomerDesc());
        master.setCreditAmount(lead.getCreditAmount());
        master.setCreditExposureAmount(lead.getCreditExposureAmount());
    }

    /**
     * 生成客户编号。
     * <p>
     * 格式：CUST_{currentTimeMillis}_{random4位数字}，如 CUST_1714032000000_5821。
     * </p>
     *
     * @return 客户编号字符串
     */
    private String generateCustNo() {
        int random4 = new Random().nextInt(9000) + 1000;
        return "CUST_" + System.currentTimeMillis() + "_" + random4;
    }
}
