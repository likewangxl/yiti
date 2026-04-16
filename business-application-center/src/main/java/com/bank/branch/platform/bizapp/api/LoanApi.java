package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;

import java.util.List;
import java.util.Optional;

/**
 * 贷款申请对外查询接口。
 * <p>
 * 供其他模块（如绩效计算中心、报表分析中心）查询贷款申请数据。
 * 实现类位于 {@code facade/LoanApiImpl}，仅在 business-application-center 模块内。
 * </p>
 */
public interface LoanApi {

    /**
     * 按申请ID查询贷款申请。
     *
     * @param applyId 申请ID（UUID，32位）
     * @return 贷款申请 DTO，不存在时返回空 Optional
     */
    Optional<LoanApplyDTO> getLoanApply(String applyId);

    /**
     * 按流程业务键查询贷款申请。
     *
     * @param businessKey 业务键，格式为 LOAN:{id}
     * @return 贷款申请 DTO，不存在时返回空 Optional
     */
    Optional<LoanApplyDTO> getLoanApplyByBusinessKey(String businessKey);

    /**
     * 查询客户的贷款申请历史列表。
     *
     * @param custId 客户ID
     * @return 贷款申请 DTO 列表，无数据时返回空列表
     */
    List<LoanApplyDTO> getCustomerLoanHistory(String custId);

    /**
     * 批量按ID查询贷款申请。
     *
     * @param applyIds 申请ID列表
     * @return 贷款申请 DTO 列表，无数据时返回空列表
     */
    List<LoanApplyDTO> getLoanApplyBatch(List<String> applyIds);
}
