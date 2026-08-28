package com.bank.branch.platform.customer.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 删除资产立项草稿请求，同时承载乐观锁版本和审计原因。 */
@Data
public class AssetProjectDeleteRequest {
    @NotNull(message = "锁版本不能为空")
    private Integer lockVersion;

    @NotBlank(message = "删除原因不能为空")
    @Size(max = 500, message = "删除原因不能超过500字")
    private String reason;
}
