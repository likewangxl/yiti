package com.bank.branch.platform.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 分配关系调整申请 Mapper (V1.2 Q2.1).
 *
 * <p>负责 perf_alloc_adjust_apply 表的 CRUD：
 * <ul>
 *   <li>BaseMapper 提供：insert(T) / selectById(Serializable) / updateById(T) / deleteById</li>
 *   <li>{@link #selectByAllocApplyId} 按申请主键查询（自定义方法，避免与 BaseMapper.selectById 冲突）</li>
 *   <li>{@link #selectByApplyNo} 按申请编号 UK 查询</li>
 *   <li>{@link #selectByBusinessKey} 按流程业务键查询（工作流回调使用）</li>
 *   <li>{@link #updateStatus} 审批过程中状态推进 + 回写 processInstanceId</li>
 *   <li>{@link #selectByConditions} 条件分页查询（status/bizKind/custId/ownerOrgId/createdBy）</li>
 *   <li>{@link #countByConditions} 条件计数（与查询保持一致）</li>
 * </ul>
 */
@Mapper
public interface PerfAllocAdjustApplyMapper extends BaseMapper<PerfAllocAdjustApply> {

    /**
     * 按申请主键查询（自定义方法，BaseMapper.selectById(Serializable) 已由继承提供）.
     *
     * @param id 申请 ID
     * @return 申请实体，不存在返回 null
     */
    PerfAllocAdjustApply selectByAllocApplyId(@Param("id") String id);

    /**
     * 按申请编号 UK 查询.
     *
     * @param applyNo 申请编号
     * @return 申请实体，不存在返回 null
     */
    PerfAllocAdjustApply selectByApplyNo(@Param("applyNo") String applyNo);

    /**
     * 按流程业务键查询（供工作流回调落地使用）.
     *
     * @param businessKey 业务键（格式 ALLOC_ADJUST:{id}）
     * @return 申请实体，不存在返回 null
     */
    PerfAllocAdjustApply selectByBusinessKey(@Param("businessKey") String businessKey);

    /**
     * 统计某客户在指定分配维度下处于审批中(IN_APPROVAL)的申请数（同客户同维度去重用）.
     *
     * <p>区别于 {@link #countByConditions}：去重粒度精确到 (cust_no + alloc_dim)，
     * 使「按规则分配」审批中的申请不阻塞「按账号分配」的新提交，反之亦然。
     *
     * @param custNo   业务客户编号
     * @param allocDim 分配维度：RULE / ACCOUNT
     * @return 审批中的申请数
     */
    long countInApprovalByCustAndDim(@Param("custNo") String custNo,
                                     @Param("allocDim") String allocDim);

    /**
     * 查询某客户在指定分配维度下「审批通过的最后一条」申请.
     *
     * <p>用于「原业绩分配」预览：按 cust_id + alloc_dim 过滤 status=APPROVED，
     * 按 created_time（申请提交时间）倒序取第一条（同时间以 id 倒序兜底）.
     *
     * @param custId   内部客户 ID（apply.cust_id；调用方先把 custNo 解析为内部 ID）
     * @param allocDim 分配维度：RULE / ACCOUNT
     * @return 最后一条审批通过的申请；无则返回 null
     */
    PerfAllocAdjustApply selectLastApprovedByCustAndDim(@Param("custId") String custId,
                                                        @Param("allocDim") String allocDim);

    /**
     * 更新状态与流程实例 ID（审批流程推进时使用）.
     *
     * <p>processInstanceId 为 null 时不更新该字段（避免清空历史值）.
     *
     * @param id                申请 ID
     * @param status            新状态
     * @param processInstanceId 流程实例 ID（可空）
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("processInstanceId") String processInstanceId);

    /**
     * 条件分页查询（支持 status/bizKind/custId/ownerOrgId/createdBy 任意组合）.
     *
     * @param status      状态过滤（nullable）
     * @param bizKind     业务种类过滤（nullable）
     * @param custId      客户 ID 过滤（nullable）
     * @param ownerOrgId  归属机构过滤（nullable，可承担数据范围条件）
     * @param createdBy   申请人过滤（nullable）
     * @param offset      偏移量
     * @param limit       每页大小
     * @return 匹配的申请列表
     */
    List<PerfAllocAdjustApply> selectByConditions(@Param("status") String status,
                                                  @Param("bizKind") String bizKind,
                                                  @Param("custId") String custId,
                                                  @Param("ownerOrgId") String ownerOrgId,
                                                  @Param("createdBy") String createdBy,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /**
     * 条件计数（与 {@link #selectByConditions} 过滤语义一致）.
     *
     * @return 总数
     */
    long countByConditions(@Param("status") String status,
                           @Param("bizKind") String bizKind,
                           @Param("custId") String custId,
                           @Param("ownerOrgId") String ownerOrgId,
                           @Param("createdBy") String createdBy);

    /**
     * V1.4 S1.3 新增：带数据范围片段的分页查询（配合 PerfScopeHelper
     * WORKFLOW_PARTICIPANT 分支注入 {@code business_key IN (...)}）.
     *
     * @param scopeFragment SQL 片段（PerfScopeHelper 受控生成，ALL 时为 ""，fail-close 时为 "1=0"）
     * @param scopeParams   预编译参数 Map（走 {@code #{scopeParams.xxx}} 占位符）
     */
    List<PerfAllocAdjustApply> selectByConditionsWithScope(@Param("status") String status,
                                                           @Param("bizKind") String bizKind,
                                                           @Param("custId") String custId,
                                                           @Param("ownerOrgId") String ownerOrgId,
                                                           @Param("createdBy") String createdBy,
                                                           @Param("offset") int offset,
                                                           @Param("limit") int limit,
                                                           @Param("scopeFragment") String scopeFragment,
                                                           @Param("scopeParams") Map<String, Object> scopeParams);

    /**
     * V1.4 S1.3 新增：带数据范围片段的条件计数（与 {@link #selectByConditionsWithScope} 语义一致）.
     */
    long countByConditionsWithScope(@Param("status") String status,
                                    @Param("bizKind") String bizKind,
                                    @Param("custId") String custId,
                                    @Param("ownerOrgId") String ownerOrgId,
                                    @Param("createdBy") String createdBy,
                                    @Param("scopeFragment") String scopeFragment,
                                    @Param("scopeParams") Map<String, Object> scopeParams);
}
