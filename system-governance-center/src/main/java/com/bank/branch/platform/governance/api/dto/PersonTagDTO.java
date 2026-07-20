package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 人员标签对外 DTO（跨模块契约，仅暴露标识与名称）。
 */
@Data
public class PersonTagDTO {

    /** 标签 ID. */
    private Long tagId;

    /** 标签名称. */
    private String tagName;
}
