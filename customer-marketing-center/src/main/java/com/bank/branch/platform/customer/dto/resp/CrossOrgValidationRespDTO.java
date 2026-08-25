package com.bank.branch.platform.customer.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 跨机构营销申请条件校验结果。 */
@Data
public class CrossOrgValidationRespDTO {
    private boolean valid;
    private String custId;
    private String custNo;
    private String custName;
    private String mainManagerId;
    private String mainOrgId;
    private LocalDateTime snapshotTime;
    private List<CheckItem> checks;

    /** 单条配置化规则校验结果。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private String ruleCode;
        private String ruleName;
        private boolean passed;
        private String message;
        private String dataSource;
    }
}
