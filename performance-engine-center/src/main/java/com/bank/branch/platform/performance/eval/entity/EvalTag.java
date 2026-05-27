package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评价标签字典表 EVAL_TAG 贫血实体.
 */
@Data
@TableName("EVAL_TAG")
public class EvalTag {
    /** 主键. */
    @TableId(value = "tag_id", type = IdType.AUTO)
    private Long tagId;
    /** 标签名称. */
    private String tagName;
    /** 标签类型：1=被评价人标签, 2=评价人标签. */
    private Integer tagType;
    /** 状态：1=启用, 0=停用. */
    private Integer status;
    /** 创建时间. */
    private LocalDateTime createTime;
    /** 更新时间. */
    private LocalDateTime updateTime;
}
