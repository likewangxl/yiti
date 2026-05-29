package com.bank.branch.platform.workflow.api.dto.flow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批流程条件分支可用的流程变量描述。
 * <p>
 * field：变量名（与 Flowable 流程变量 key 严格对应）<br>
 * label：中文展示名（供前端条件构造器下拉展示）<br>
 * type ：变量类型（string / number / boolean），供条件构造器渲染合适的输入控件
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowVariableDTO {

    /** 变量字段名，与 Flowable 流程变量 key 严格对应 */
    private String field;

    /** 中文展示名，供前端条件构造器下拉列表展示 */
    private String label;

    /** 变量类型：string / number / boolean */
    private String type;
}
