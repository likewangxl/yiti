package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 人员标签列表行响应（含关联人数）。 */
@Data
public class PersonTagRespDTO {

    /** 标签 ID. */
    private Long tagId;

    /** 标签名称. */
    private String tagName;

    /** 备注. */
    private String remark;

    /** 创建人. */
    private String createBy;

    /** 创建时间. */
    private LocalDateTime createTime;

    /** 更新时间. */
    private LocalDateTime updateTime;

    /** 关联人数. */
    private Long memberCount;
}
