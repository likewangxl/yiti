package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

/** 用户分页查询请求 DTO */
@Data
public class UserQueryReqDTO {
    /** 登录名模糊匹配 */
    private String username;
    /** 中文名模糊匹配 */
    private String userchnname;
    /** 邮箱模糊匹配 */
    private String email;
    /** 备注模糊匹配 */
    private String remark;
    /** 启用状态过滤 0-启用 1-未启用 */
    private Integer isEnabled;
    /** 锁定状态过滤 0-未锁定 1-已锁定 */
    private Integer isLocked;
    /** 页码（从1开始） */
    private Integer pageNo = 1;
    /** 每页条数 */
    private Integer pageSize = 20;
}
