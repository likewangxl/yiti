package com.bank.branch.platform.report.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 业绩调整分配预览响应 DTO（原业绩分配）.
 *
 * <p>数据来源已由「数据湖存贷款余额 + CORP_ASSET_LIAB_ALLOT」改为 perf 的当前原分配关系：
 * 从 {@code CUST_ALLOC_RELATION.is_original='2'} 候选中按来源批次取最新一批的全部关系
 * （详见 perf {@code AllocApi.getLastApprovedAllocPreview}）。
 * ACCOUNT 仅在 ACCOUNT 候选中选最新批次；RULE/null 在 RULE+ACCOUNT 候选中选整体最新批次。
 */
@Data
public class AllocPreviewRespDTO {

    /** 是否查到有效数据（false 表示该客户暂无当前 is_original='2' 的最新来源批次关系）. */
    private boolean hasData;

    /** 原业绩分配列表. */
    private List<AllocItem> allocList;

    @Data
    public static class AllocItem {
        /** 账号（按规则分配维度为空，按账号分配维度取 relation.account_no）. */
        private String acctNo;
        /** 分配维度：RULE / ACCOUNT. */
        private String allocDim;
        /** 员工登录名（PT_USER.USERNAME；由 perf 按 USER_ID 批量映射，缺失时回退 USER_ID）. */
        private String username;
        /** 员工中文姓名（relation.fullname 快照）. */
        private String empChnName;
        /** 所属机构号（relation.dept_no 快照）. */
        private String orgCode;
        /** 所属机构名称（relation.dept_name 快照）. */
        private String orgName;
        /** 分配比例（relation.ratio，0-100）. */
        private String ratio;
    }
}
