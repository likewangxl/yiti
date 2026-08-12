package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 命名机构组直接成员实体，对应 {@code PT_ORG_GROUP_MEMBER}。 */
@Data
@TableName("PT_ORG_GROUP_MEMBER")
public class PtOrgGroupMember {

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private String groupCode;
    private String orgCode;
    private String status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private String remark;
}
