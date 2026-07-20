package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 人员标签成员行响应（工号/姓名/机构）。 */
@Data
public class PersonTagMemberRespDTO {

    /** 关联行 ID（PERSON_TAG_REL.ID）. */
    private Long id;

    /** 员工工号（PT_USER.USERNAME）. */
    private String username;

    /** 员工姓名（PT_USER 实时解析，用户已删除时为 null）. */
    private String displayName;

    /** 主机构编码. */
    private String orgCode;

    /** 主机构名称. */
    private String orgName;

    /** 关联创建时间. */
    private LocalDateTime createTime;
}
