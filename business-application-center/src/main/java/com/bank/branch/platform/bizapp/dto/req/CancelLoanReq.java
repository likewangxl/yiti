package com.bank.branch.platform.bizapp.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资产立项申请撤回请求 DTO。
 * <p>
 * 撤回属于高风险写操作，必须携带可审计的撤回原因；Loan/LOAN 仅作为接口兼容标识保留。
 * </p>
 */
@Data
public class CancelLoanReq {

    /** 撤回原因（必填，最长500字符） */
    @NotBlank(message = "撤回原因不能为空")
    @Size(max = 500, message = "撤回原因长度不能超过500")
    private String reason;
}
