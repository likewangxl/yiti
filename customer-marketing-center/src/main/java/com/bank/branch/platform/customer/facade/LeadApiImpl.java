package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.LeadApi;
import com.bank.branch.platform.customer.api.converter.LeadDTOConverter;
import com.bank.branch.platform.customer.api.dto.LeadDTO;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 线索对外接口实现。
 * <p>
 * 实现 {@link LeadApi} 接口，直接委托 {@link CustLeadMapper} 完成只读查询。
 * 返回值统一使用 DTO（{@link LeadDTO}），不向调用方暴露内部实体。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadApiImpl implements LeadApi {

    private final CustLeadMapper custLeadMapper;

    /**
     * 按 ID 查询线索详情，结果转为 DTO。
     *
     * @param leadId 线索 ID
     * @return 包含 LeadDTO 的 Optional；不存在时返回 Optional.empty()
     */
    @Override
    public Optional<LeadDTO> getLead(String leadId) {
        log.debug("[LeadApiImpl.getLead] leadId={}", leadId);
        return Optional.ofNullable(LeadDTOConverter.toDTO(custLeadMapper.selectById(leadId)));
    }

    /**
     * 按业务键查询线索，结果转为 DTO。
     *
     * @param businessKey 业务键
     * @return 包含 LeadDTO 的 Optional；不存在时返回 Optional.empty()
     */
    @Override
    public Optional<LeadDTO> getLeadByBusinessKey(String businessKey) {
        log.debug("[LeadApiImpl.getLeadByBusinessKey] businessKey={}", businessKey);
        return Optional.ofNullable(LeadDTOConverter.toDTO(custLeadMapper.selectByBusinessKey(businessKey)));
    }

    /**
     * 查询批次下的所有线索。
     *
     * @param importBatchId 导入批次 ID
     * @return 线索 DTO 列表；无数据时返回空列表
     */
    @Override
    public List<LeadDTO> getLeadsByBatch(String importBatchId) {
        log.debug("[LeadApiImpl.getLeadsByBatch] importBatchId={}", importBatchId);
        return LeadDTOConverter.toDTOList(custLeadMapper.selectByImportBatchId(importBatchId));
    }

    /**
     * 查询线索的完整版本链，按 version_no 升序返回。
     * <p>
     * 策略：先查当前记录获取 sourceCustId，再按 sourceCustId 拉取全链（order by version_no）。
     * 若为首版（sourceCustId=null），则仅返回自身。
     * </p>
     *
     * @param leadId 线索 ID（任意版本均可）
     * @return 版本链 DTO 列表；查不到时返回空列表
     */
    @Override
    public List<LeadDTO> getLeadVersionChain(String leadId) {
        log.debug("[LeadApiImpl.getLeadVersionChain] leadId={}", leadId);
        CustLead current = custLeadMapper.selectById(leadId);
        if (current == null) {
            return Collections.emptyList();
        }
        // 首版记录 sourceCustId 为空，直接返回自身
        String sourceCustId = current.getSourceCustId();
        if (sourceCustId == null) {
            return List.of(LeadDTOConverter.toDTO(current));
        }
        // 通过 sourceCustId 查同一客户全量版本链（XML 中已按 version_no ASC 排序）
        return LeadDTOConverter.toDTOList(
                custLeadMapper.selectBySourceCustIdOrderByVersion(sourceCustId));
    }

    /**
     * 校验客户名称是否可用（全行唯一）。
     * <p>
     * 空白名称直接视为不可用；编辑场景通过 excludeLeadId 排除自身记录。
     * 仅统计状态为 PENDING_APPROVAL / APPROVED 的有效线索。
     * </p>
     *
     * @param custName      客户名称
     * @param excludeLeadId 编辑场景下排除的线索 ID，新建场景传 null
     * @return true 表示名称可用，false 表示已被占用
     */
    @Override
    public boolean isLeadCustNameAvailable(String custName, String excludeLeadId) {
        log.debug("[LeadApiImpl.isLeadCustNameAvailable] custName={}, excludeLeadId={}", custName, excludeLeadId);
        if (custName == null || custName.isBlank()) {
            return false;
        }
        Long count = custLeadMapper.countActiveByCustName(custName, excludeLeadId);
        return count == null || count == 0L;
    }
}
