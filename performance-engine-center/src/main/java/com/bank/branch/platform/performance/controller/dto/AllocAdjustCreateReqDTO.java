package com.bank.branch.platform.performance.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分配关系调整申请创建请求 DTO (V1.2 Q2.4).
 *
 * <p>对应端点：{@code POST /api/perf/alloc-adjust/create}
 */
@Data
public class AllocAdjustCreateReqDTO {

    /** 客户 ID（必填）. */
    @NotBlank(message = "custId 必填")
    private String custId;

    /** 分配维度：RULE / ACCOUNT（必填）. */
    @NotBlank(message = "allocDim 必填")
    private String allocDim;

    /** 业务种类（必填，决定流程路由：CORP_* → corp_v1，RETAIL_* → retail_v1）. */
    @NotBlank(message = "bizKind 必填")
    private String bizKind;

    /** 账号（ACCOUNT 维度必填，RULE 维度可空）. */
    private String accountNo;

    /** 归属机构（必填，数据范围基准）. */
    @NotBlank(message = "ownerOrgId 必填")
    private String ownerOrgId;

    /** 申请原因（必填，审计留痕）. */
    @Size(max = 500, message = "reason 长度不超过 500")
    private String reason;

    /** 明细（必填，empId 去重）. */
    @NotNull(message = "items 必填")
    private List<Item> items;

    /**
     * 调整明细项.
     */
    @Data
    public static class Item {

        /** 调整后归属员工工号. */
        @NotBlank(message = "item.empId 必填")
        private String empId;

        /** 调整后分配比例（0-100）. */
        @NotNull(message = "item.ratio 必填")
        private BigDecimal ratio;
    }
}
