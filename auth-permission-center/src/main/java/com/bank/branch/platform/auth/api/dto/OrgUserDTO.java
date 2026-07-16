package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 机构下用户简要信息DTO（G.2）
 * 用于 /api/orgs/{orgCode}/users 接口返回值
 *
 * <p>前端用户管理「按机构筛选」复用此接口，所以同步透传状态/锁定/创建时间，
 * 避免列表少几列空白；同时为兼容老调用方保留 empId / displayName 字段名。</p>
 */
@Data
public class OrgUserDTO {

    /** 工号 */
    private String empId;

    /** 用户名 */
    private String username;

    /** 中文姓名 */
    private String displayName;

    /** 用户邮箱 */
    private String email;

    /** 备注 */
    private String remark;

    /** 部门名称（EXT_USER_ORG ⋈ EXT_ORG_INFO 的 ORG_NAME，多机构以「、」连接；无归属为 null） */
    private String deptName;

    /** 启用状态：0-启用，1-停用 */
    private Integer isEnabled;

    /** 锁定状态：0-未锁定，1-已锁定 */
    private Integer isLocked;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 角色列表 */
    private List<RoleSimpleDTO> roles;
}
