package com.bank.branch.platform.redengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;

/** 党支部任务分配 Mapper。 */
public interface ReTaskBranchAssignmentMapper extends BaseMapper<ReTaskBranchAssignment> {

    /**
     * 在任务、实例、支部和最新提交状态条件下分页查询 assignment。
     *
     * <p>该方法必须由 SQL 在分页前完成筛选；不能把所有 assignment 读入内存后再拼装页签，
     * 否则审核工作台的 total 会随当前页错误变化。</p>
     */
    IPage<ReTaskBranchAssignment> selectWorkflowPage(
            IPage<?> page,
            @Param("assignmentIds") Collection<Long> assignmentIds,
            @Param("instanceIds") Collection<Long> instanceIds,
            @Param("branchIds") Collection<Long> branchIds,
            @Param("assignmentStatuses") Collection<String> assignmentStatuses,
            @Param("submissionStatuses") Collection<String> submissionStatuses,
            @Param("taskNature") String taskNature,
            @Param("businessType") String businessType,
            @Param("cycle") String cycle,
            @Param("keyword") String keyword,
            @Param("submitterIds") Collection<String> submitterIds,
            @Param("submittedStartAt") LocalDateTime submittedStartAt,
            @Param("submittedEndAt") LocalDateTime submittedEndAt);
}
