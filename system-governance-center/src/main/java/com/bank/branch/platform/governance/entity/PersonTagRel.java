package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人员标签-人员关联实体，对应 PERSON_TAG_REL 表（一人可挂多个标签）。
 * <p>唯一键：UK_PTR_TAG_USER (TAG_ID, USERNAME)。</p>
 * <p>USERNAME 存员工工号（PT_USER.USERNAME 口径），不是 USER_ID 代理键。</p>
 */
@Data
@TableName("PERSON_TAG_REL")
public class PersonTagRel {

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /** 标签 ID（PERSON_TAG.TAG_ID）. */
    private Long tagId;

    /** 员工工号（PT_USER.USERNAME）. */
    private String username;

    /** 创建人. */
    private String createBy;

    /** 创建时间（DB CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime createTime;
}
