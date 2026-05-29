package com.bank.branch.platform.workflow.api.dto.flow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 审批流程节点条件 DTO
 * <p>
 * logic: 多条件之间的逻辑关系，取值 "AND" / "OR"
 * conditions: 条件列表，每项描述一个字段比较规则
 * </p>
 */
@Data
public class FlowConditionDTO {

    /** 条件组逻辑关系："AND" 或 "OR"（不区分大小写） */
    private String logic;

    /** 条件列表 */
    private List<Cond> conditions;

    /**
     * 单条条件
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Cond {

        /** 字段名，对应流程变量名 */
        private String field;

        /**
         * 比较运算符，支持：
         * EQ / NE / GT / GE / LT / LE / CONTAINS / IN / NOT_IN
         */
        private String op;

        /**
         * 比较值；IN / NOT_IN 时用逗号分隔多个值
         */
        private String value;
    }
}
