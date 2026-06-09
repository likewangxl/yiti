package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * KPI 方案 DTO.
 * <p>v1.2: id 为 String (对齐生产 DDL varchar(32)).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiSchemeDTO {
    /** 方案 ID (varchar 32). */
    private String id;
    private String schemeCode;
    private String schemeName;
    /** MONTHLY/QUARTERLY/YEARLY. */
    private String cycleType;
    /** 是否向员工开放明细. */
    private Boolean openDetail;
    /** 状态: ACTIVE/DISABLED. */
    private String status;
    /** 创建人内部ID（PT_USER.USER_ID，= createdBy 原值，内部用）. */
    private String createdBy;
    /** 创建人工号（PT_USER.username，列表"工号"展示用）. */
    private String createdByUsername;
    /** 创建人姓名（PT_USER.userchnname，列表展示用）. */
    private String createdByName;
    /** 是否当前用户创建（列表控制编辑/删除按钮显隐）. */
    private Boolean createdByMe;
    /** 方案内指标项. */
    private List<KpiItemDTO> items;
}
