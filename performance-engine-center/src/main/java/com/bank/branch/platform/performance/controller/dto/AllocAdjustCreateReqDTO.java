package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** 客户类型：CORP（对公）/ RETAIL（零售），决定审批流路由. */
    @NotBlank(message = "custType 必填")
    private String custType;

    /** 客户编号（必填，存入 PERF_ALLOC_ADJUST_APPLY.cust_id）. */
    @NotBlank(message = "custId 必填")
    private String custId;

    /** 客户名称（前端反显，提交时快照入库）. */
    private String custName;

    /** 当前余额（前端反显，提交时快照入库）. */
    private BigDecimal currBal;

    /** 月均余额（前端反显，提交时快照入库）. */
    @JsonProperty("mAvgBal")
    private BigDecimal mAvgBal;

    /** 季日均余额（前端反显，提交时快照入库）. */
    @JsonProperty("qAvgBal")
    private BigDecimal qAvgBal;

    /** 年日均余额（前端反显，提交时快照入库）. */
    @JsonProperty("yAvgBal")
    private BigDecimal yAvgBal;

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
     * 原业绩分配（手工录入）. 系统自动查到历史分配时可空；
     * 查不到时由前端手工录入，提交校验要求「原业绩分配（历史或手工）至少 1 条」.
     */
    private List<OriginalItem> originalAllocList;

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

        /** 说明（可空）. */
        private String remark;
    }

    /**
     * 原业绩分配项（手工录入）. 除账号外均必填.
     */
    @Data
    public static class OriginalItem {

        /** 账号（选填）. */
        private String acctNo;

        /** 员工工号（必填）. */
        @NotBlank(message = "originalItem.empId 必填")
        private String empId;

        /** 员工登录名（前端下拉快照，可空）. */
        private String username;

        /** 员工中文姓名（前端下拉快照，可空）. */
        private String empChnName;

        /** 所属机构号（必填）. */
        @NotBlank(message = "originalItem.orgCode 必填")
        private String orgCode;

        /** 所属机构名称（前端下拉快照，可空）. */
        private String orgName;

        /** 分配比例（必填）. */
        @NotNull(message = "originalItem.ratio 必填")
        private BigDecimal ratio;
    }
}
