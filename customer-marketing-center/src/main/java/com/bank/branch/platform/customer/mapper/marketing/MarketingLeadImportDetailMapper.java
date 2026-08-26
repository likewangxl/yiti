package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_LEAD_IMPORT_DETAIL 数据访问。 */
@Mapper
public interface MarketingLeadImportDetailMapper extends BaseMapper<MarketingLeadImportDetail> {

    List<MarketingLeadImportDetail> selectByBatchIdOrderByFailure(@Param("batchId") Long batchId,
                                                                  @Param("offset") int offset,
                                                                  @Param("limit") int limit);

    long countByBatchId(@Param("batchId") Long batchId);

    List<MarketingLeadImportDetail> selectByBatchAndValidationStatus(
            @Param("batchId") Long batchId, @Param("validationStatus") String validationStatus);

    int updateHandlingIf(@Param("id") Long id,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("handlingStatus") String handlingStatus,
                         @Param("generatedLeadId") Long generatedLeadId);
}
