package com.bank.branch.platform.bizapp.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建中场支持申请请求 DTO。
 */
@Data
public class CreateSupportReq {

    /** 客户ID（必填） */
    @NotBlank(message = "custId不能为空")
    private String custId;

    /** 产品ID列表（场景A必填，场景B可选） */
    private List<String> productIds;

    /** 来源触达任务ID（可选） */
    private String sourceTouchTaskId;

    /** 其他需求/补充说明（场景B必填） */
    private String otherDemand;

    /** 承接部门ID（场景B必填） */
    private String supportDeptId;
}
