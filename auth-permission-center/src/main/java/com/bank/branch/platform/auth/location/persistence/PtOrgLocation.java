package com.bank.branch.platform.auth.location.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** PT_ORG_LOCATION 位置台账实体。正式表由 DBA 按冻结模型实施，本类不触发 DDL。 */
@Data
@TableName("PT_ORG_LOCATION")
public class PtOrgLocation {

    @TableId(value = "ORG_CODE", type = IdType.INPUT)
    private String orgCode;
    private String address;
    private String addressSource;
    private String cityCode;
    private BigDecimal lng;
    private BigDecimal lat;
    private String coordSys;
    private String provider;
    private String matchLevel;
    private String status;
    private Integer version;
    private String locationSource;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
