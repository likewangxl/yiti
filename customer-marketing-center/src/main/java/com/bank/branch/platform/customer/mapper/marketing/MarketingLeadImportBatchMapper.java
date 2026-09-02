package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_LEAD_IMPORT_BATCH 数据访问。 */
@Mapper
public interface MarketingLeadImportBatchMapper extends BaseMapper<MarketingLeadImportBatch> {

    List<MarketingLeadImportBatch> selectPage(@Param("keyword") String keyword,
                                              @Param("status") String status,
                                              @Param("importEmpId") String importEmpId,
                                              @Param("allScope") boolean allScope,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("importEmpId") String importEmpId,
                   @Param("allScope") boolean allScope);

    MarketingLeadImportBatch selectActiveById(@Param("id") Long id);

    MarketingLeadImportBatch selectForUpdate(@Param("id") Long id);

    int updateStatusIf(@Param("id") Long id,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("targetStatus") String targetStatus,
                       @Param("updatedBy") String updatedBy,
                       @Param("confirmAction") String confirmAction,
                       @Param("confirmRemark") String confirmRemark);
}
