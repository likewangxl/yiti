package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色与命名机构组授权实体，对应 {@code PT_ROLE_ORG_GROUP}。 */
@Data
@TableName("PT_ROLE_ORG_GROUP")
public class PtRoleOrgGroup {

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private String roleId;
    private String groupCode;
    private String status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private String remark;
}
