package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CrossOrgMarketingApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 跨机构营销申请 Mapper。 */
@Mapper
public interface CrossOrgMarketingApplyMapper extends BaseMapper<CrossOrgMarketingApply> {
    /** CAS 审核通过，保证重复操作不会重复建任务。 */
    @Update("UPDATE CROSS_ORG_MARKETING_APPLY SET status='APPROVED', reviewed_by=#{item.reviewedBy}, " +
            "reviewed_time=#{item.reviewedTime}, updated_by=#{item.updatedBy}, updated_time=#{item.updatedTime} " +
            "WHERE id=#{item.id} AND status='PENDING'")
    int updatePendingToApproved(@Param("item") CrossOrgMarketingApply item);

    /** CAS 审核退回。 */
    @Update("UPDATE CROSS_ORG_MARKETING_APPLY SET status='REJECTED', reject_reason=#{item.rejectReason}, " +
            "reviewed_by=#{item.reviewedBy}, reviewed_time=#{item.reviewedTime}, " +
            "updated_by=#{item.updatedBy}, updated_time=#{item.updatedTime} WHERE id=#{item.id} AND status='PENDING'")
    int updatePendingToRejected(@Param("item") CrossOrgMarketingApply item);

    /** 回填审批通过后生成的触达任务。 */
    @Update("UPDATE CROSS_ORG_MARKETING_APPLY SET generated_touch_task_id=#{taskId}, updated_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int updateGeneratedTask(@Param("id") String id, @Param("taskId") String taskId);
}
