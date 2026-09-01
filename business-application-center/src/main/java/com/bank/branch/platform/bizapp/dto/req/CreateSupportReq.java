package com.bank.branch.platform.bizapp.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 创建中台支持申请请求 DTO。 */
@Data
public class CreateSupportReq {

    /** 来源类型：TOUCH_TASK / EXISTING_CUSTOMER。兼容旧客户端时可省略。 */
    private String sourceType;

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

    /** 客户已有在途申请时是否确认并行发起。未确认时由服务端返回 BIZ-40907。 */
    private Boolean confirmParallel;

    /** 创建页上传的附件文件ID，创建后关联到每一条拆单申请。 */
    private List<String> attachmentIds;
}
