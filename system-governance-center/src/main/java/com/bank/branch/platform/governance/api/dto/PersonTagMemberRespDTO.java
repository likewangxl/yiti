package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务标签成员行响应。
 * <p>按维度分两类：EMP=员工（仅工号，姓名已按需求去除）、ORG=机构（机构编号 dept_no + 机构名称实时解析）。</p>
 */
@Data
public class PersonTagMemberRespDTO {

    /** 关联行 ID（PERSON_TAG_REL.ID）. */
    private Long id;

    /** 成员维度：EMP=员工 / ORG=机构. */
    private String dimType;

    /** 员工工号（PT_USER.USERNAME）；DIM_TYPE=EMP 时有值. */
    private String username;

    /** 机构业务编号（EXT_ORG_INFO.DEPT_NO）；DIM_TYPE=ORG 时有值. */
    private String orgDeptNo;

    /** 机构名称（DIM_TYPE=ORG 时按 dept_no 实时解析，机构不存在时为 null）. */
    private String orgName;

    /** 关联创建时间. */
    private LocalDateTime createTime;
}
