package com.bank.branch.platform.customer.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 线索标签快照响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadTagRespDTO {
    private String tagId;
    private String tagName;
}
