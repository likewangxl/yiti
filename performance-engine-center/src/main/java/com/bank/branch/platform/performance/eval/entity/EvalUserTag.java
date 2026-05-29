package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 人员标签关联表 EVAL_USER_TAG 贫血实体.
 */
@Data
@TableName("EVAL_USER_TAG")
public class EvalUserTag {
    /** 主键. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 人员ID，关联 PT_USER.USER_ID（工号，String）. */
    private String userId;
    /** 标签ID，关联 EVAL_TAG.TAG_ID. */
    private Long tagId;
}
