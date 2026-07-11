package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 新建评价规则请求体。
 */
@Data
public class CreateRuleReq {
    /** 规则名称（必填，不能为空白）. */
    @NotBlank
    private String ruleName;
    /** 被评价人标签ID（必填，唯一约束）. */
    @NotNull
    private Long beEvalTagId;
    /** 评价人组列表. */
    private List<GroupReq> groups;
}
