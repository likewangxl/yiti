package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待处理任务批次 EVAL_ASSIGN_BATCH 贫血实体.
 * <p>一次导入对应一批，承载来源/类型/截止时间等批级元数据。</p>
 */
@Data
@TableName("EVAL_ASSIGN_BATCH")
public class EvalAssignBatch {
    /** 主键. */
    @TableId(value = "batch_id", type = IdType.AUTO)
    private Long batchId;
    /** 批次名称（可空）. */
    private String batchName;
    /** 待处理任务类型（字典 EVAL_IMPORT_TYPE：EVAL/REWARD）. */
    private String taskType;
    /** 来源（字典 EVAL_PENDING_SOURCE：IMPORT/AUTO）. */
    private String source;
    /** 打分截止时间. */
    private LocalDateTime deadline;
    /** 状态：0=进行中, 1=已结束, 2=草稿, 3=处理中(IMPORTING), 4=导入失败(IMPORT_FAILED). */
    private Integer status;
    /** 解析出的总行数（异步导入处理结束时回填，DB 列 TOTAL_ROWS）. */
    private Integer totalRows;
    /** 成功入库条数（全部校验通过时回填，DB 列 IMPORTED_COUNT）. */
    private Integer importedCount;
    /** 失败时行级错误明细 JSON（封顶前 N 条，DB 列 ERROR_SUMMARY）. */
    private String errorSummary;
    /** 创建人工号. */
    private String createBy;
    /** 创建时间. */
    private LocalDateTime createTime;
    /** 明细数量（仅列表查询填充，非 DB 列）. */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long itemCount;
}
