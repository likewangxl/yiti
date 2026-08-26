package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_LEAD_INFO 数据访问。 */
@Mapper
public interface MarketingLeadInfoMapper extends BaseMapper<MarketingLeadInfo> {

    List<MarketingLeadInfo> selectManualPage(@Param("keyword") String keyword,
                                             @Param("status") String status,
                                             @Param("entryEmpId") String entryEmpId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    long countManualPage(@Param("keyword") String keyword,
                         @Param("status") String status,
                         @Param("entryEmpId") String entryEmpId);

    MarketingLeadInfo selectActiveByCreditCode(@Param("creditCode") String creditCode);

    MarketingLeadInfo selectActiveById(@Param("id") Long id);

    MarketingLeadInfo selectForUpdate(@Param("id") Long id);

    MarketingLeadInfo selectForUpdateByCreditCode(@Param("creditCode") String creditCode);

    int updateStatusIf(@Param("id") Long id,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("targetStatus") String targetStatus,
                       @Param("updatedBy") String updatedBy,
                       @Param("rejectReason") String rejectReason);

    List<MarketingLeadInfo> selectByImportBatchId(@Param("batchId") Long batchId);
}
