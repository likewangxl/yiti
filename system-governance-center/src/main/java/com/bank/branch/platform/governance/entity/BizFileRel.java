package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务-附件关联实体，对应 biz_file_rel 表。
 * <p>
 * 建立业务对象与文件对象之间的多对多关联关系。
 * 通过 biz_type + biz_id + file_object_id 唯一约束保证幂等性。
 * </p>
 */
@Data
@TableName("biz_file_rel")
public class BizFileRel {

    /** 关联ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 业务类型（如 LEAD、CUSTOMER），对应 biz_type */
    private String bizType;

    /** 业务ID（字符串），对应 biz_id */
    private String bizId;

    /** 文件对象ID（关联 file_object.id），对应 file_object_id */
    private String fileObjectId;

    /** 文件用途（ATTACHMENT / PHOTO / ...），对应 file_role */
    private String fileRole;

    /** 创建人，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;
}
