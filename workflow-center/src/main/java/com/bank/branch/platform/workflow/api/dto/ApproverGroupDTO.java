package com.bank.branch.platform.workflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 一组机构负责人审批人快照。
 *
 * <p>该 DTO 会作为 Flowable 流程变量保存，因此必须保持可序列化；组内审批人
 * 使用或签语义，组之间由顺序多实例保证全部完成。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproverGroupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 机构编码，作为流程机构快照的稳定键。 */
    private String groupKey;

    /** 机构名称，作为任务名称和审批记录的展示快照。 */
    private String groupName;

    /** 当前机构负责人候选人工号列表，组内任一人完成即代表本组完成。 */
    private List<String> approverEmpIds;
}
