package com.bank.branch.platform.customer.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssetProjectUrgentRequest {
    @NotBlank(message = "加急原因不能为空")
    @Size(max = 1000, message = "加急原因不能超过1000字")
    private String reason;
}
