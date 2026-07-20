package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人员标签创建请求。 */
@Data
public class PersonTagCreateReqDTO {

    /** 标签名称（全局唯一）. */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称最长 100 字符")
    private String tagName;

    /** 备注. */
    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
