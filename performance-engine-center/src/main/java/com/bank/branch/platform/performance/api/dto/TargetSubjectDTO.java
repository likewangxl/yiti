package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目标值「对象」下拉项 DTO（目标值管理页查询区的对象下拉，按方案内目标值去重）.
 *
 * <p>展示口径：
 * <ul>
 *   <li>EMP 维度：{@code displayId}=工号（PT_USER.username）、{@code name}=姓名；label=「工号 姓名」</li>
 *   <li>ORG 维度：{@code displayId}=部门编号（EXT_ORG_INFO.DEPT_NO）、{@code name}=机构名称；label=「部门编号 机构名称」</li>
 * </ul>
 * {@code subjectId} 始终是入库口径（EMP=工号、ORG=内部机构编码），供前端作为列表查询过滤值回传。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetSubjectDTO {

    /** 对象类型：EMP / ORG. */
    private String subjectType;

    /** 入库对象ID（EMP=工号；ORG=内部机构编码）——作为列表过滤回传值. */
    private String subjectId;

    /** 展示用编号（EMP=工号；ORG=部门编号）. */
    private String displayId;

    /** 名称（EMP=姓名；ORG=机构名称）. */
    private String name;

    /** 下拉展示文本（EMP=「工号 姓名」；ORG=「部门编号 机构名称」）. */
    private String label;
}
