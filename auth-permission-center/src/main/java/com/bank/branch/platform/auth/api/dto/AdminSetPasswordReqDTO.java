package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员为指定用户设置新密码请求 DTO。
 * <p>区别于 ChangeMyPasswordReqDTO（用户改自己），此 DTO 不要求旧密码，
 * 由管理员（具备 PERMISSION_CHANGE 权限）直接覆盖目标用户密码。</p>
 */
@Data
public class AdminSetPasswordReqDTO {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度须在 6~64 之间")
    private String newPassword;

    @Size(max = 200, message = "原因不超过 200 字")
    private String reason;
}
