package com.bank.branch.platform.customer.dto.marketing.tag;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 标签客户审批前置依赖响应。 */
@Data
@AllArgsConstructor
public class TagApprovalRequiredResponse {
    private Long tagId;
    private String tagName;
    private Long batchId;
    private List<Long> detailIds;
}
