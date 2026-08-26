package com.bank.branch.platform.customer.dto.marketing.lead;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 导入批次待确认动作。 */
@Data
public class LeadImportConfirmRequest {
    @NotBlank(message = "确认动作不能为空")
    private String action;

    @Size(max = 500, message = "确认说明长度不能超过500")
    private String remark;
}
