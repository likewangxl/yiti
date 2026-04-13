package com.bank.branch.platform.portal.controller.dto.addrbook;

import lombok.Data;

/**
 * 通讯录员工查询请求 DTO（C.1 分页查询）
 */
@Data
public class EmployeeQueryReqDTO {

    /** 关键字搜索（工号/姓名模糊匹配） */
    private String keyword;

    /** 机构编码过滤 */
    private String orgCode;

    /** 岗位过滤 */
    private String position;

    /** 状态过滤：ACTIVE / RESIGNED */
    private String status;

    /** 页码（默认 1） */
    private Integer pageNo = 1;

    /** 每页大小（默认 20） */
    private Integer pageSize = 20;
}
