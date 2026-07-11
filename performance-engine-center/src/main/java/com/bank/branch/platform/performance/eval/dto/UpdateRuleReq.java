package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 更新评价规则请求体。
 */
@Data
public class UpdateRuleReq {
    /** 规则名称（必填，不能为空白）. */
    @NotBlank
    private String ruleName;
    /** 评价人组列表. */
    private List<GroupReq> groups;
}
