package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务标签-成员关联实体，对应 PERSON_TAG_REL 表（一个标签可挂多个成员，一人/一机构可挂多标签）。
 * <p>成员按维度（{@link #dimType}）分两类：EMP=员工（{@link #username} 有值）、ORG=机构
 * （{@link #orgDeptNo} 有值）。同一标签下两类成员可共存。</p>
 * <p>唯一键：UK_PTR_TAG_USER (TAG_ID, USERNAME) 约束员工成员、UK_PTR_TAG_ORG (TAG_ID, ORG_DEPT_NO)
 * 约束机构成员（另一维度对应列为 NULL，MySQL 允许多 NULL，两维互不冲突）。</p>
 * <p>USERNAME 存员工工号（PT_USER.USERNAME 口径，不是 USER_ID 代理键）；
 * ORG_DEPT_NO 存机构业务编号（EXT_ORG_INFO.DEPT_NO 口径，不是内部 org_code）。</p>
 */
@Data
@TableName("PERSON_TAG_REL")
public class PersonTagRel {

    /** 成员维度：员工. */
    public static final String DIM_EMP = "EMP";
    /** 成员维度：机构. */
    public static final String DIM_ORG = "ORG";

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /** 标签 ID（PERSON_TAG.TAG_ID）. */
    private Long tagId;

    /** 成员维度：EMP=员工 / ORG=机构（DB 默认 EMP，存量行即员工）. */
    private String dimType;

    /** 员工工号（PT_USER.USERNAME）；DIM_TYPE=EMP 时有值，ORG 行为 NULL. */
    private String username;

    /** 机构业务编号（EXT_ORG_INFO.DEPT_NO）；DIM_TYPE=ORG 时有值，EMP 行为 NULL. */
    private String orgDeptNo;

    /** 创建人. */
    private String createBy;

    /** 创建时间（DB CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime createTime;
}
