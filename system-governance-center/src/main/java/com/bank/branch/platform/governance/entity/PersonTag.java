package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人员标签实体，对应 PERSON_TAG 表（全平台通用）。
 * <p>唯一键：UK_PERSON_TAG_NAME (TAG_NAME)。</p>
 */
@Data
@TableName("PERSON_TAG")
public class PersonTag {

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "TAG_ID", type = IdType.AUTO)
    private Long tagId;

    /** 标签名称（全局唯一）. */
    private String tagName;

    /** 备注. */
    private String remark;

    /** 创建人. */
    private String createBy;

    /** 创建时间（DB CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime createTime;

    /** 更新人. */
    private String updateBy;

    /** 更新时间（DB ON UPDATE CURRENT_TIMESTAMP 填充）. */
    private LocalDateTime updateTime;
}
