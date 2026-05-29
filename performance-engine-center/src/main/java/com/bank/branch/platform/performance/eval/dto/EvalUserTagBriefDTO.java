package com.bank.branch.platform.performance.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 标签简要信息（列表/编辑回显用）：仅 tagId + tagName。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvalUserTagBriefDTO {
    /** 标签ID. */
    private Long tagId;
    /** 标签名称. */
    private String tagName;
}
