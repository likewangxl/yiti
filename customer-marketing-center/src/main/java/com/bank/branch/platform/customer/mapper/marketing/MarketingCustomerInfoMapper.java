package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerProfileUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerQuery;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 营销客户主档 Mapper，仅操作 MARKETING_CUSTOMER_INFO。 */
@Mapper
public interface MarketingCustomerInfoMapper extends BaseMapper<MarketingCustomerInfo> {

    /** 查询有效营销客户；详情和写服务均使用该方法避免读到失效档案。 */
    MarketingCustomerInfo selectActiveById(@Param("id") Long id);

    /** 认领提交前锁定客户主档，避免主办权在校验后发生变化。 */
    MarketingCustomerInfo selectForUpdate(@Param("id") Long id);

    /**
     * 按信用代码或客户名称精确查询有效客户，最多读取两条用于识别名称歧义。
     * 信用代码非空时由 SQL 优先使用信用代码，忽略客户名称。
     */
    List<MarketingCustomerInfo> selectActiveMatches(
            @Param("unifiedCreditCode") String unifiedCreditCode,
            @Param("customerName") String customerName);

    /** 按页面条件分页查询客户主档。 */
    List<MarketingCustomerInfo> selectPage(@Param("query") MarketingCustomerQuery query,
                                           @Param("scopeType") String scopeType,
                                           @Param("orgCodes") Set<String> orgCodes,
                                           @Param("mineEmpId") String mineEmpId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 统计页面条件下客户主档总数。 */
    long countPage(@Param("query") MarketingCustomerQuery query,
                   @Param("scopeType") String scopeType,
                   @Param("orgCodes") Set<String> orgCodes,
                   @Param("mineEmpId") String mineEmpId);

    /** 以 profile_version + lock_version 双版本做客户资料 CAS 更新。 */
    int updateProfileByVersions(@Param("id") Long id,
                                @Param("profileVersion") Integer profileVersion,
                                @Param("lockVersion") Integer lockVersion,
                                @Param("profile") MarketingCustomerProfileUpdateRequest profile,
                                @Param("updatedBy") String updatedBy,
                                @Param("updatedTime") LocalDateTime updatedTime);

    /** 以 lock_version 做主办权 CAS 更新；同时固定切为 MANUAL。 */
    int updateOwnershipByLockVersion(@Param("id") Long id,
                                     @Param("lockVersion") Integer lockVersion,
                                     @Param("managerId") String managerId,
                                     @Param("orgId") String orgId,
                                     @Param("ownershipStatus") String ownershipStatus,
                                     @Param("ownershipMaintainMode") String ownershipMaintainMode,
                                     @Param("updatedBy") String updatedBy,
                                     @Param("updatedTime") LocalDateTime updatedTime,
                                     @Param("manualReason") String manualReason);
}
