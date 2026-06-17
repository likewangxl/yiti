package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/**
 * 用户通讯录视图 DTO（PT_USER + EXT_USER_ORG + EXT_ORG_INFO 三表联查结果）
 *
 * <p>用于替代原 portal {@code ADDRBOOK_EMPLOYEE} 的员工查询数据源（员工选择器/审批人选择）。
 * 字段名与原 {@code EmployeeSearchDTO/EmployeeDetailDTO} 保持一致，保证前端展示口径不变。</p>
 *
 * <p>口径说明（与本系统既有约定一致）：</p>
 * <ul>
 *   <li>{@code empId} = {@code PT_USER.USER_ID}（代理键，全系统 empId 的规范取值，
 *       与角色展开 empId、会签 assignee 一致）；非工号。</li>
 *   <li>{@code empName} = {@code PT_USER.USERCHNNAME}（中文姓名）。</li>
 *   <li>{@code orgCode/orgName} 取自用户主机构（EXT_USER_ORG → EXT_ORG_INFO）。</li>
 *   <li>{@code position} 三表无来源，恒为 null（仅占位，保持 DTO 结构一致）。</li>
 *   <li>{@code status} 由 ISENABLED 推导：在职统一返回 {@code ACTIVE}。</li>
 * </ul>
 */
@Data
public class UserDirectoryDTO {

    /** 员工标识（= PT_USER.USER_ID 代理键，非工号） */
    private String empId;

    /** 员工姓名（中文姓名 USERCHNNAME） */
    private String empName;

    /** 主机构编码 */
    private String orgCode;

    /** 主机构名称 */
    private String orgName;

    /** 岗位（三表无来源，恒为 null，保持与原通讯录 DTO 结构一致） */
    private String position;

    /** 在职状态（在职恒为 ACTIVE，对齐原通讯录口径） */
    private String status;
}
