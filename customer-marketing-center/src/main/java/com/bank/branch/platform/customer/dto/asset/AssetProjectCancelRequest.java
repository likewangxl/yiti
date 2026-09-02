package com.bank.branch.platform.customer.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssetProjectCancelRequest {
    @NotBlank(message = "撤回原因不能为空")
    @Size(max = 1000, message = "撤回原因不能超过1000字")
    private String reason;
}
