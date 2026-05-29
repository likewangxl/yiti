package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 人员评价设置表 EVAL_USER_SETTING 贫血实体。
 * <p>无该工号记录视为"否"（未启用）。</p>
 */
@Data
@TableName("EVAL_USER_SETTING")
public class EvalUserSetting {
    /** 工号，主键，关联 PT_USER.USER_ID（String）. */
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;
    /** 是否启用评价：1=是 0=否. */
    private Integer evalEnabled;
    /** 创建时间. */
    private LocalDateTime createdTime;
    /** 最近更新时间. */
    private LocalDateTime updatedTime;
}
