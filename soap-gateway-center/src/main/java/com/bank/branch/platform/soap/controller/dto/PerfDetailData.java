package com.bank.branch.platform.soap.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** PERF_INFO 成功载荷：对齐手机端 applyInfo.vue 的 dataForm 字段。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfDetailData {
    private String perfAdjustNo;
    private String applyFullname;
    private String custId;
    private String custName;
    /** 1=公司业绩调整 / 2=零售业绩调整。 */
    private String applyType;
    /** 1=账号调整 / 2=规则调整。 */
    private String applyRule;
    private String iouNo;
    private String businessType;
    private String adjustExplain;
    /** 0=待审核 1=已同意 2=已拒绝 3=已撤回。 */
    private String apprStatus;
    /** 1=可审批 / 0=否。 */
    private Integer isCanAppr;
    /** 1=可撤回 / 0=否。 */
    private Integer isCanDelete;
    /** 分配明细：原分配(isOriginal=1) + 调整后(isOriginal=2)。 */
    private List<PerfAllocItem> allocaters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerfAllocItem {
        private String username;
        private String fullname;
        private String ratio;
        private Integer isOriginal;
    }
}
