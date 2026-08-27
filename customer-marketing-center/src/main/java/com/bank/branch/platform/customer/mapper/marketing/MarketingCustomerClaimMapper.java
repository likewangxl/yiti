package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.dto.resp.ClaimedCustomerRespDTO;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_CUSTOMER_CLAIM 认领及两个客户池组合查询。 */
@Mapper
public interface MarketingCustomerClaimMapper extends BaseMapper<MarketingCustomerClaim> {

    /** 查询审批通过、公开、可认领且当前员工未有效认领的线索客户。 */
    List<CustomerDTO> selectAvailablePoolPage(@Param("keyword") String keyword,
                                              @Param("empId") String empId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /** 与 {@link #selectAvailablePoolPage} 完全相同条件的总数。 */
    long countAvailablePoolPage(@Param("keyword") String keyword,
                                @Param("empId") String empId);

    /** 按与待认领池列表完全相同的条件校验来源线索是否仍对当前员工可见。 */
    int countAvailableLead(@Param("leadId") Long leadId,
                           @Param("empId") String empId);

    /** 查询本人已认领客户及来源、取消原因和最新触达任务。 */
    List<ClaimedCustomerRespDTO> selectClaimedCustomerPage(@Param("empId") String empId,
                                                           @Param("tab") String tab,
                                                           @Param("keyword") String keyword,
                                                           @Param("sourceType") String sourceType,
                                                           @Param("offset") int offset,
                                                           @Param("limit") int limit);

    /** 与 {@link #selectClaimedCustomerPage} 完全相同条件的总数。 */
    long countClaimedCustomerPage(@Param("empId") String empId,
                                  @Param("tab") String tab,
                                  @Param("keyword") String keyword,
                                  @Param("sourceType") String sourceType);

    /** 认领提交前检查同一线索、同一员工是否已有有效关系。 */
    MarketingCustomerClaim selectActiveBySourceLeadAndClaimedBy(
            @Param("sourceLeadId") Long sourceLeadId,
            @Param("claimedBy") String claimedBy);

    /** 取消/触达前锁定目标认领行。 */
    MarketingCustomerClaim selectForUpdate(@Param("id") Long id);
}
