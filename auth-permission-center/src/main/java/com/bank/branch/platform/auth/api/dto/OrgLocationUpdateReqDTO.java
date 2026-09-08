package com.bank.branch.platform.auth.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 机构地址/坐标覆盖更新请求。坐标来源、状态和匹配精度由服务端决定。 */
@Data
public class OrgLocationUpdateReqDTO {

    @Size(max = 255)
    private String address;

    @Size(max = 12)
    private String cityCode;

    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal lng;

    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal lat;

    @Size(max = 10)
    private String coordSys;

    /** 人工坐标必须由操作员显式确认。 */
    private Boolean manualConfirmed;

    /** 地址解析候选确认令牌；服务端从令牌恢复坐标、精度和来源。 */
    @Size(max = 4096)
    private String candidateToken;

    /** 明确清除已保存坐标；地址本身仍可保留。 */
    private Boolean clearLocation;

    /** 更新和新增均要求显式版本，新增时使用 0。 */
    private Integer version;

    /** 高影响配置变更原因。 */
    @Size(max = 500)
    private String reason;

    /*
     * 下列字段仅为兼容客户端误传/旧表单，服务端永远不采信；若有值将拒绝请求，
     * 防止客户端伪造 status、source 或精度。
     */
    @Size(max = 32)
    private String addressSource;
    @Size(max = 32)
    private String provider;
    @Size(max = 32)
    private String matchLevel;
    @Size(max = 32)
    private String status;
    @Size(max = 32)
    private String locationSource;
}
