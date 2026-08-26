package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_CUSTOMER_TAG_IMPORT_DETAIL 的 MyBatis-Plus Mapper。 */
@Mapper
public interface MarketingCustomerTagImportDetailMapper extends BaseMapper<MarketingCustomerTagImportDetail> {

    /** 按批次分页读取明细，失败/异常优先。 */
    List<MarketingCustomerTagImportDetail> selectPageByBatchId(@Param("batchId") Long batchId,
                                                               @Param("approvalStatus") String approvalStatus,
                                                               @Param("keyword") String keyword,
                                                               @Param("offset") int offset,
                                                               @Param("limit") int limit);

    /** 统计批次明细数量。 */
    long countByBatchId(@Param("batchId") Long batchId,
                        @Param("approvalStatus") String approvalStatus,
                        @Param("keyword") String keyword);

    /** 查询批次下全部明细，供导入预览和替换收敛使用。 */
    List<MarketingCustomerTagImportDetail> selectByBatchId(@Param("batchId") Long batchId);

    /** 查询标签下待审批客户，页面六按标签聚合。 */
    List<MarketingCustomerTagImportDetail> selectPendingByTagId(@Param("tagId") Long tagId,
                                                                 @Param("keyword") String keyword,
                                                                 @Param("offset") int offset,
                                                                 @Param("limit") int limit);

    /** 查询本人已办理的客户审批记录。 */
    List<MarketingCustomerTagImportDetail> selectHistoryByReviewer(@Param("reviewedBy") String reviewedBy,
                                                                    @Param("keyword") String keyword,
                                                                    @Param("offset") int offset,
                                                                    @Param("limit") int limit);

    /** 统计本人已办理的客户审批记录。 */
    long countHistoryByReviewer(@Param("reviewedBy") String reviewedBy,
                                @Param("keyword") String keyword);

    /** 查询同一批次中指定明细，后端重新校验前端传入的 ID。 */
    List<MarketingCustomerTagImportDetail> selectByBatchIdAndIds(@Param("batchId") Long batchId,
                                                                 @Param("ids") List<Long> ids);
}
