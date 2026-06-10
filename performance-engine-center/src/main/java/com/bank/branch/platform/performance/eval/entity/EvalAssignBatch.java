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
    /** 状态：0=进行中, 1=已结束. */
    private Integer status;
    /** 创建人工号. */
    private String createBy;
    /** 创建时间. */
    private LocalDateTime createTime;
}
