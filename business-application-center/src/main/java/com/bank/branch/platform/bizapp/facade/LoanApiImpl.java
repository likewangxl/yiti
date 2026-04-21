package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.LoanApi;
import com.bank.branch.platform.bizapp.api.converter.LoanApplyDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 贷款申请对外查询接口实现。
 * <p>
 * 实现 {@link LoanApi} 接口，直接委托 {@link LoanApplyMapper} 完成只读查询。
 * 所有查询结果通过 {@link LoanApplyDTOConverter} 转换为跨模块传输对象，
 * converter 负责补充 custName 冗余字段并确保不暴露 deleted 字段。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanApiImpl implements LoanApi {

    private final LoanApplyMapper loanApplyMapper;
    private final LoanApplyDTOConverter loanApplyDTOConverter;

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
        return Optional.ofNullable(entity).map(loanApplyDTOConverter::toDTO);
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
        return Optional.ofNullable(entity).map(loanApplyDTOConverter::toDTO);
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
        // 使用批量转换避免 N+1
        return loanApplyDTOConverter.toDTOList(entities);
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
        // 使用批量转换避免 N+1
        return loanApplyDTOConverter.toDTOList(entities);
    }
}
