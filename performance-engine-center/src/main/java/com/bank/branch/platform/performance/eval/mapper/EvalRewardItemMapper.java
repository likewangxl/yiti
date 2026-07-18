package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 奖励分配明细 Mapper。
 * <p>单条 CRUD 由 BaseMapper 提供；以下为自定义方法（批量插入、聚合、条件更新）。</p>
 */
@Mapper
public interface EvalRewardItemMapper extends BaseMapper<EvalRewardItem> {

    /** 批量插入明细。 */
    int batchInsert(@Param("items") List<EvalRewardItem> items);

    /**
     * 查询某分配人未提交明细，按 batch_id + 部门聚合（仅 ACTIVE status=0 且未过期批次）。
     *
     * @param assignUserId 分配人工号(USER_ID)
     * @return 汇总行(batchId/taskType/taskName/dept/pendingCount/assignTotal/deadline)
     */
    List<EvalRewardPendingGroupDTO> selectRewardPendingGroups(@Param("assignUserId") String assignUserId);

    /** 查询某分配人在指定批次+部门下的全部明细（含已提交，处理列表展示）。 */
    List<EvalRewardItem> selectByAssignerBatchDept(@Param("assignUserId") String assignUserId,
                                                   @Param("batchId") Long batchId,
                                                   @Param("dept") String dept);

    /** 标记某明细已分配并写入分配值/兑现值(=原始值+分配值,由 Service 计算)与提交时间（带 submitted=0 乐观条件）。 */
    int markAssigned(@Param("itemId") Long itemId,
                     @Param("assignValue") BigDecimal assignValue,
                     @Param("cashValue") BigDecimal cashValue,
                     @Param("submitTime") LocalDateTime submitTime);

    /** 管理端-分页查询批次下明细。 */
    List<EvalRewardItem> selectByBatchId(@Param("batchId") Long batchId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 管理端-批次下明细总数。 */
    long countByBatchId(@Param("batchId") Long batchId);

    /** 批次下去重的全部分配人 USER_ID（导出反查工号用）。 */
    List<String> selectDistinctAssignerIdsByBatch(@Param("batchId") Long batchId);

    /** 管理端-按条件分页查询 REWARD 批次列表（含明细数 itemCount）。 */
    List<EvalAssignBatch> selectRewardBatchesByCondition(@Param("status") Integer status,
                                                         @Param("keyword") String keyword,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    /** 管理端-按条件统计 REWARD 批次数。 */
    long countRewardBatchesByCondition(@Param("status") Integer status,
                                       @Param("keyword") String keyword);

    /** 按批次ID删除全部明细。 */
    int deleteByBatchId(@Param("batchId") Long batchId);
}
