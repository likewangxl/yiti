package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.LoanApi;
import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 贷款申请对外查询接口实现。
 * <p>
 * 实现 {@link LoanApi} 接口，直接委托 {@link LoanApplyMapper} 完成只读查询。
 * 所有查询结果通过私有 {@code toDTO()} 方法转换为跨模块传输对象。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanApiImpl implements LoanApi {

    private final LoanApplyMapper loanApplyMapper;

    /**
     * 按申请ID查询贷款申请。
     *
     * @param applyId 申请ID
     * @return 贷款申请 DTO，不存在时返回空 Optional
     */
    @Override
    public Optional<LoanApplyDTO> getLoanApply(String applyId) {
        log.debug("[LoanApiImpl.getLoanApply] applyId={}", applyId);
        LoanApply entity = loanApplyMapper.selectById(applyId);
        return Optional.ofNullable(entity).map(this::toDTO);
    }

    /**
     * 按流程业务键查询贷款申请。
     *
     * @param businessKey 业务键，格式为 LOAN:{id}
     * @return 贷款申请 DTO，不存在时返回空 Optional
     */
    @Override
    public Optional<LoanApplyDTO> getLoanApplyByBusinessKey(String businessKey) {
        log.debug("[LoanApiImpl.getLoanApplyByBusinessKey] businessKey={}", businessKey);
        LoanApply entity = loanApplyMapper.selectByBusinessKey(businessKey);
        return Optional.ofNullable(entity).map(this::toDTO);
    }

    /**
     * 查询客户的贷款申请历史列表。
     *
     * @param custId 客户ID
     * @return 贷款申请 DTO 列表，无数据时返回空列表
     */
    @Override
    public List<LoanApplyDTO> getCustomerLoanHistory(String custId) {
        log.debug("[LoanApiImpl.getCustomerLoanHistory] custId={}", custId);
        List<LoanApply> entities = loanApplyMapper.selectByCustId(custId);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 批量按ID查询贷款申请。
     *
     * @param applyIds 申请ID列表
     * @return 贷款申请 DTO 列表，无数据时返回空列表
     */
    @Override
    public List<LoanApplyDTO> getLoanApplyBatch(List<String> applyIds) {
        log.debug("[LoanApiImpl.getLoanApplyBatch] count={}", applyIds == null ? 0 : applyIds.size());
        if (applyIds == null || applyIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<LoanApply> entities = loanApplyMapper.selectByIds(applyIds);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // 私有转换方法
    // ------------------------------------------------------------------

    /**
     * 将 LoanApply 实体转换为对外 DTO。
     * 手动逐字段赋值，避免引入额外的 mapping 框架依赖。
     *
     * @param entity 贷款申请实体
     * @return 对外 DTO
     */
    private LoanApplyDTO toDTO(LoanApply entity) {
        LoanApplyDTO dto = new LoanApplyDTO();
        dto.setId(entity.getId());
        dto.setApplyNo(entity.getApplyNo());
        dto.setCustId(entity.getCustId());
        dto.setSourceTouchTaskId(entity.getSourceTouchTaskId());
        dto.setProjectType(entity.getProjectType());
        dto.setBizType(entity.getBizType());
        dto.setGuaranteeType(entity.getGuaranteeType());
        dto.setCreditAmount(entity.getCreditAmount());
        dto.setCreditExposureAmount(entity.getCreditExposureAmount());
        dto.setStatus(entity.getStatus());
        dto.setBusinessKey(entity.getBusinessKey());
        dto.setProcessInstanceId(entity.getProcessInstanceId());
        dto.setOwnerOrgId(entity.getOwnerOrgId());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedTime(entity.getUpdatedTime());
        dto.setDeleted(entity.getDeleted());
        return dto;
    }
}
