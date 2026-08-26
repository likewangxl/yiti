package com.bank.branch.platform.customer.dto.marketing.tag;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量客户审批逐条结果。 */
@Data
public class TagApprovalResult {
    private int successCount;
    private int skippedCount;
    private int failedCount;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long detailId;
        private String result;
        private String message;

        public Item() {
        }

        public Item(Long detailId, String result, String message) {
            this.detailId = detailId;
            this.result = result;
            this.message = message;
        }
    }
}
