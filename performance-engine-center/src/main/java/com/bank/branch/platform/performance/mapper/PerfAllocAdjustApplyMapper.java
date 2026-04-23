package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分配关系调整申请 Mapper (V1.2 Q2.1).
 *
 * <p>负责 perf_alloc_adjust_apply 表的 CRUD：
 * <ul>
 *   <li>{@link #insert} 插入新申请</li>
 *   <li>{@link #selectById} 按主键查询</li>
 *   <li>{@link #selectByApplyNo} 按申请编号 UK 查询</li>
 *   <li>{@link #selectByBusinessKey} 按流程业务键查询（工作流回调使用）</li>
 *   <li>{@link #updateStatus} 审批过程中状态推进 + 回写 processInstanceId</li>
 *   <li>{@link #selectByConditions} 条件分页查询（status/bizKind/custId/ownerOrgId/createdBy）</li>
 *   <li>{@link #countByConditions} 条件计数（与查询保持一致）</li>
 * </ul>
 */
@Mapper
public interface PerfAllocAdjustApplyMapper {

    /**
     * 插入新的调整申请.
     *
     * @param apply 申请实体（id/applyNo/custId/allocDim/bizKind/status/ownerOrgId/createdBy 必填）
     * @return 受影响行数
     */
    int insert(PerfAllocAdjustApply apply);

    /**
     * 按主键查询.
     *
     * @param id 申请 ID
     * @return 申请实体，不存在返回 null
     */
    PerfAllocAdjustApply selectById(@Param("id") String id);

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
}
