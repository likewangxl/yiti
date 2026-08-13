package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 机构本地画像新增/更新请求。 */
@Data
public class OrgProfileUpdateReqDTO {

    @NotBlank
    @Size(max = 30)
    private String orgNature;

    @NotBlank
    @Size(max = 20)
    private String operatingLevel;

    @Size(max = 20)
    private String ownerOperatingOrgCode;

    @Size(max = 20)
    private String cityCode;

    @Size(max = 100)
    private String cityName;

    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal lng;

    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal lat;

    @Size(max = 10)
    private String coordSys;

    @Size(max = 10)
    private String status;

    /** 更新已有画像时使用；新增画像可为空。 */
    private Integer version;

    @Size(max = 500)
    private String remark;

    /** 高影响配置变更原因，供审计切面采集。 */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
