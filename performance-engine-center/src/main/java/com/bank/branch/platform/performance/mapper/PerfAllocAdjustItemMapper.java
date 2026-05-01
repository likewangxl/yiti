package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分配关系调整明细 Mapper (V1.2 Q2.1).
 *
 * <p>负责 perf_alloc_adjust_item 表的 CRUD：
 * <ul>
 *   <li>{@link #batchInsert} 批量插入明细（申请创建时一次性写入）</li>
 *   <li>{@link #selectByApplyId} 按申请 ID 查询明细列表</li>
 *   <li>{@link #deleteByApplyId} 按申请 ID 清空（更新草稿时可复用）</li>
 * </ul>
 *
 * <p>UK {@code (apply_id, emp_id)} 保证同一申请中每个员工只出现一次。
 * <p>BaseMapper 标准方法由 MyBatis-Plus 提供.
 */
@Mapper
public interface PerfAllocAdjustItemMapper extends BaseMapper<PerfAllocAdjustItem> {

    /**
     * 批量插入明细.
     *
     * @param items 明细列表（非空，每个 item 的 id/applyId/empId/ratio 必填）
     * @return 受影响行数
     */
    int batchInsert(@Param("items") List<PerfAllocAdjustItem> items);

    /**
     * 按申请 ID 查询所有明细.
     *
     * @param applyId 申请 ID
     * @return 明细列表（按 created_time 升序）
     */
    List<PerfAllocAdjustItem> selectByApplyId(@Param("applyId") String applyId);

    /**
     * 按申请 ID 清空明细（DRAFT 状态下更新申请可复用，IN_APPROVAL 后禁止调用）.
     *
     * @param applyId 申请 ID
     * @return 受影响行数
     */
    int deleteByApplyId(@Param("applyId") String applyId);
}
