package com.bank.branch.platform.customer.dto.req;

import lombok.Data;

/**
 * 提交线索审批请求 DTO。
 * <p>
 * 提交审批时可携带可选备注，正文可为空。
 * </p>
 */
@Data
public class LeadSubmitReqDTO {

    /** 提交备注（可为空） */
    private String remark;
}
