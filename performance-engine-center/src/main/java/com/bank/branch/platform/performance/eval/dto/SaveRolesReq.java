package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

/**
 * 覆盖式保存人员评价角色（单标签）及参与评价开关请求体。
 */
@Data
public class SaveRolesReq {
    /** 标签ID（null 表示清空角色）. */
    private Long tagId;
    /** 是否参与评价：1=是 0=否；null 兜底为参与. */
    private Integer evalEnabled;
}
