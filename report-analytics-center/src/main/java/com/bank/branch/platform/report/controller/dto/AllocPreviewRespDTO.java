package com.bank.branch.platform.report.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 业绩调整分配预览响应 DTO
 */
@Data
public class AllocPreviewRespDTO {

    /** 是否查到有效数据（false 时前端应阻止提交） */
    private boolean hasData;

    /** 存款汇总（人民币） */
    private BalanceSummary depositSummary;

    /** 贷款汇总（人民币） */
    private BalanceSummary loanSummary;

    /** 原业绩分配列表 */
    private List<AllocItem> allocList;

    @Data
    public static class BalanceSummary {
        /** 当前余额（折人民币） */
        private BigDecimal currBalRmb = BigDecimal.ZERO;
        /** 当月日均余额（折人民币） */
        private BigDecimal currMAvgBalRmb = BigDecimal.ZERO;
        /** 当年日均余额（折人民币） */
        private BigDecimal currYAvgBalRmb = BigDecimal.ZERO;
    }

    @Data
    public static class AllocItem {
        /** 账号/借据号 */
        private String acctNo;
        /** 分配比例 */
        private String dynScale;
        /** 员工号 */
        private String allocaterId;
        /** 员工姓名 */
        private String empName;
        /** 所属机构 */
        private String orgName;
    }
}
