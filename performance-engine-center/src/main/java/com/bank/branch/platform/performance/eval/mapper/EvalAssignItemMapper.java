package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.dto.EvalPendingGroupDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待处理任务明细 Mapper。
 * <p>单条 CRUD（insert / selectById / update）由 MyBatis-Plus BaseMapper 提供；
 * 以下为 BaseMapper 无法覆盖的自定义方法（批量插入、聚合统计、条件更新）。</p>
 */
@Mapper
public interface EvalAssignItemMapper extends BaseMapper<EvalAssignItem> {

    /** 批量插入明细（BaseMapper 无 mapper 级批量插入，自定义实现）。 */
    int batchInsert(@Param("items") List<EvalAssignItem> items);

    /**
     * 查询某打分人未提交明细，按 batch_id + 被打分人部门聚合（跨表 JOIN 聚合，自定义 SQL）。
     *
     * @param evalUserId 打分人工号
     * @return 汇总行（batchId / taskType / dept / pendingCount / deadline）
     */
    List<EvalPendingGroupDTO> selectPendingGroups(@Param("evalUserId") String evalUserId);

    /**
     * 查询某打分人在指定批次+部门下的全部明细（含已提交，用于处理列表展示）。
     */
    List<EvalAssignItem> selectByScorerBatchDept(@Param("evalUserId") String evalUserId,
                                                 @Param("batchId") Long batchId,
                                                 @Param("dept") String dept);

    /** 标记某明细已提交并写入分数与提交时间（带 submitted=0 乐观条件，自定义更新）。 */
    int markSubmitted(@Param("itemId") Long itemId,
                      @Param("score") Integer score,
                      @Param("submitTime") LocalDateTime submitTime);
}
