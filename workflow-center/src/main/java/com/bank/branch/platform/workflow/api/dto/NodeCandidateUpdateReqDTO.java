package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 流程节点候选人更新请求 DTO
 */
@Data
public class NodeCandidateUpdateReqDTO {

    /** 候选类型：ROLE / ORG / USER */
    private String candidateType;

    /** 候选值列表 */
    private List<String> candidateValue;
}
