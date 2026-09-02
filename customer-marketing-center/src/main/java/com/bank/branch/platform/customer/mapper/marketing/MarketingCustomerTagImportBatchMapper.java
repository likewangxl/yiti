package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_CUSTOMER_TAG_IMPORT_BATCH 的 MyBatis-Plus Mapper。 */
@Mapper
public interface MarketingCustomerTagImportBatchMapper extends BaseMapper<MarketingCustomerTagImportBatch> {

    /** 按业务批次号查询。 */
    MarketingCustomerTagImportBatch selectByBatchNo(@Param("batchNo") String batchNo);

    /** 按导入人和状态分页查询批次。 */
    List<MarketingCustomerTagImportBatch> selectPage(@Param("keyword") String keyword,
                                                     @Param("tagId") Long tagId,
                                                     @Param("status") String status,
                                                     @Param("importEmpId") String importEmpId,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    /** 统计导入批次总数。 */
    long countPage(@Param("keyword") String keyword,
                   @Param("tagId") Long tagId,
                   @Param("status") String status,
                   @Param("importEmpId") String importEmpId);

    /** 查询同一标签是否存在未结束的全量替换批次。 */
    long countInFlightReplace(@Param("tagId") Long tagId,
                              @Param("excludeBatchId") Long excludeBatchId);

    /** 仅在当前状态下推进批次，保证重复请求幂等。 */
    int updateStatusIf(@Param("id") Long id,
                       @Param("fromStatus") String fromStatus,
                       @Param("toStatus") String toStatus,
                       @Param("updatedBy") String updatedBy);

    /** 更新批次统计和汇总状态。 */
    int updateSummary(@Param("id") Long id,
                      @Param("customerApprovalStatus") String customerApprovalStatus,
                      @Param("status") String status,
                      @Param("pendingApprovalCount") int pendingApprovalCount,
                      @Param("approvedCount") int approvedCount,
                      @Param("rejectedCount") int rejectedCount,
                      @Param("loadedCount") int loadedCount,
                      @Param("completedTime") java.time.LocalDateTime completedTime,
                      @Param("replaceBlockReason") String replaceBlockReason,
                      @Param("updatedBy") String updatedBy);
}
