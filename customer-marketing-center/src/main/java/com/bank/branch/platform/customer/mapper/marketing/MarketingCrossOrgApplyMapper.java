package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingCrossOrgApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MARKETING_CROSS_ORG_APPLY 数据访问。 */
@Mapper
public interface MarketingCrossOrgApplyMapper extends BaseMapper<MarketingCrossOrgApply> {

    /** 防止同一客户、同一申请人重复存在有效申请。 */
    long countActiveByCustomerAndApplicant(@Param("custId") Long custId,
                                           @Param("applicantEmpId") String applicantEmpId);

    /** IN_APPROVAL -> APPROVED CAS；返回 0 表示已由其他请求处理。 */
    int updateInApprovalToApproved(@Param("item") MarketingCrossOrgApply item);

    /** IN_APPROVAL -> REJECTED CAS。 */
    int updateInApprovalToRejected(@Param("item") MarketingCrossOrgApply item);

    /** 审批通过后回写唯一生成的触达任务。 */
    int updateGeneratedTouchTask(@Param("id") Long id,
                                 @Param("taskId") Long taskId);
}
