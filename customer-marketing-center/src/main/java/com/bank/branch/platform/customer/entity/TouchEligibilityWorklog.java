package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 触达资格统计所需的工作日志最小映射。
 * <p>独立于触达工作日志迁移实体，服务只读取有效日志计数。</p>
 */
@Data
@TableName("xa_touch_custom_worklogs")
public class TouchEligibilityWorklog {

    @TableId(value = "workLogId", type = IdType.INPUT)
    private String workLogId;

    @TableField("companyUSCI")
    private String companyUSCI;

    @TableField("createTime")
    private LocalDateTime createTime;

    @TableField("isDestroy")
    private String isDestroy;
}
