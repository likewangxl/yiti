package com.bank.branch.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 机构本地经营画像，对应 {@code PT_ORG_PROFILE}。
 *
 * <p>画像是本地配置，不回写外部同步表 {@code EXT_ORG_INFO}。机构性质和经营等级
 * 有意拆成两个正交字段，允许“部门 + 一级经营机构”等合法组合。</p>
 */
@Data
@TableName("PT_ORG_PROFILE")
public class PtOrgProfile {

    /** 机构业务编码，引用 EXT_ORG_INFO.ORG_CODE，不建跨表外键。 */
    @TableId(value = "ORG_CODE", type = IdType.INPUT)
    private String orgCode;

    private String orgNature;
    private String operatingLevel;
    private String ownerOperatingOrgCode;
    private String cityCode;
    private String cityName;
    private BigDecimal lng;
    private BigDecimal lat;
    private String coordSys;
    private String status;
    private Integer version;

    @TableField("CREATED_BY")
    private String createdBy;

    @TableField("CREATED_TIME")
    private LocalDateTime createdTime;

    @TableField("UPDATED_BY")
    private String updatedBy;

    @TableField("UPDATED_TIME")
    private LocalDateTime updatedTime;

    private String remark;
}
