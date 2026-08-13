package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 大屏级查看角色白名单覆盖保存请求。 */
@Data
public class ScreenAccessRoleSaveReqDTO {

    @NotNull
    private List<String> roleCodes;

    /** 高影响权限配置变更原因。 */
    @NotBlank
    private String reason;

    /** 屏配置版本 CAS；权限白名单不能覆盖他人已保存的配置。 */
    @NotNull
    private Integer expectedVersion;
}
