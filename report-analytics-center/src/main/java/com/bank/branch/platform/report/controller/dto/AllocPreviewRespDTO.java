package com.bank.branch.platform.report.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 业绩调整分配预览响应 DTO（原业绩分配）.
 *
 * <p>数据来源已由「数据湖存贷款余额 + CORP_ASSET_LIAB_ALLOT」改为
 * perf 的分配关系调整申请：对客户分别取 RULE / ACCOUNT 维度下审批通过的最后一条申请明细
 * （详见 perf {@code AllocApi.getLastApprovedAllocPreview}）。
 */
@Data
public class AllocPreviewRespDTO {

    /** 是否查到有效数据（false 表示该客户暂无审批通过的分配调整申请）. */
    private boolean hasData;

    /** 原业绩分配列表. */
    private List<AllocItem> allocList;

    @Data
    public static class AllocItem {
        /** 账号（按规则分配维度为空，按账号分配维度取申请账号）. */
        private String acctNo;
        /** 分配维度：RULE / ACCOUNT. */
        private String allocDim;
        /** 员工登录名（username）. */
        private String username;
        /** 员工中文姓名. */
        private String empChnName;
        /** 所属机构号. */
        private String orgCode;
        /** 所属机构名称. */
        private String orgName;
        /** 分配比例（0-100）. */
        private String ratio;
    }
}
