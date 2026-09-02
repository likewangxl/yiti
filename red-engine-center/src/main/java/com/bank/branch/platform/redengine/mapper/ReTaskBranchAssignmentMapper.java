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

    /**
     * 在任务详情场景按任务维度完成支部 assignment 的数据库筛选与分页。
     *
     * <p>与工作台查询分开保留参数契约，避免管理端为了得到任务实例 ID 而先加载全部实例；
     * 任务、实例、最新提交及关键字条件均在分页前由 SQL 执行。</p>
     *
     * @param page 分页对象
     * @param taskId 任务定义 ID
     * @param branchId 可选支部 ID
     * @param status 可选 assignment 状态
     * @param keyword 可选支部/提交信息关键字
     * @param submitterIds 关键字命中的提交人 ID 候选
     * @param submittedStartAt 填报时间起点
     * @param submittedEndAt 填报时间终点
     * @return 数据库分页结果
     */
    IPage<ReTaskBranchAssignment> selectTaskAssignmentPage(
            IPage<?> page,
            @Param("taskId") Long taskId,
            @Param("branchId") Long branchId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("submitterIds") Collection<String> submitterIds,
            @Param("submittedStartAt") LocalDateTime submittedStartAt,
            @Param("submittedEndAt") LocalDateTime submittedEndAt);
}
