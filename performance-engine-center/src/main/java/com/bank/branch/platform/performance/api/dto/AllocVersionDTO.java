package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 分配关系版本 DTO (派生自 sys_control scope_dim=CUST).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocVersionDTO {
    /** 业务种类 (V1.0 可以为 null, 表示全部). */
    private String bizKind;
    /** 固定 "CUST". */
    private String scopeDim;
    /** 当前版本号, 格式 v{N}. */
    private String currentVersion;
    /** 版本生效日. */
    private LocalDate latestDataDate;
    /** 版本发布时间. */
    private LocalDateTime publishedAt;
    /** 发布人工号. */
    private String publishedBy;
}
