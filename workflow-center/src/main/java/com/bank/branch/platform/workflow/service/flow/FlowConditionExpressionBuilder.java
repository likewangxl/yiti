package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 审批流程条件表达式（EL）生成器
 * <p>
 * 将 {@link FlowConditionDTO} 转换为 Flowable/Spring EL 表达式字符串，
 * 供流程定义中网关条件（sequenceFlow conditionExpression）使用。
 * </p>
 *
 * <p>支持的运算符：EQ / NE / GT / GE / LT / LE / CONTAINS / IN / NOT_IN</p>
 *
 * <p>
 * 数字识别规则：value 匹配 {@code -?\d+(\.\d+)?} 时直接拼入（不加引号），
 * 否则用单引号包裹，内部单引号转义为 \'。
 * </p>
 */
@Component
public class FlowConditionExpressionBuilder {

    /**
     * 将条件 DTO 转换为 EL 表达式。
     *
     * @param c 条件 DTO，logic 取 "AND"/"OR"（忽略大小写）
     * @return EL 表达式字符串，如 {@code ${bizKind == 'CORP'}}；
     *         c 为 null 或 conditions 为空时返回 null
     */
    public String toEl(FlowConditionDTO c) {
        if (c == null || c.getConditions() == null || c.getConditions().isEmpty()) {
            return null;
        }
        // logic 为 OR 时用 " || "，否则（含 AND 及其他默认值）用 " && "
        String joiner = "OR".equalsIgnoreCase(c.getLogic()) ? " || " : " && ";
        String body = c.getConditions().stream()
                .map(this::one)
                .collect(Collectors.joining(joiner));
        return "${" + body + "}";
    }

    /**
     * 将单条 Cond 转换为 EL 片段。
     *
     * @param x 单条条件
     * @return EL 片段字符串
     * @throws IllegalArgumentException 遇到不支持的运算符时抛出
     */
    private String one(FlowConditionDTO.Cond x) {
        String f = x.getField();
        String v = x.getValue();
        switch (x.getOp()) {
            case "EQ":      return f + " == " + lit(v);
            case "NE":      return f + " != " + lit(v);
            case "GT":      return f + " > " + v;
            case "GE":      return f + " >= " + v;
            case "LT":      return f + " < " + v;
            case "LE":      return f + " <= " + v;
            case "CONTAINS": return f + ".contains('" + esc(v) + "')";
            case "IN":      return inExpr(f, v, "==", " || ");
            case "NOT_IN":  return inExpr(f, v, "!=", " && ");
            default:
                throw new IllegalArgumentException("不支持的运算符: " + x.getOp());
        }
    }

    /**
     * 生成 IN / NOT_IN 括号表达式。
     *
     * @param field 字段名
     * @param csv   逗号分隔的值列表
     * @param eq    比较符（"==" 或 "!="）
     * @param join  连接符（" || " 或 " && "）
     * @return 括号包裹的 EL 片段，如 {@code (f == 'A' || f == 'B')}
     */
    private String inExpr(String field, String csv, String eq, String join) {
        String body = Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(s -> field + " " + eq + " '" + esc(s) + "'")
                .collect(Collectors.joining(join));
        return "(" + body + ")";
    }

    /**
     * 生成字面量：纯数字（含负数、小数）不加引号，其余加单引号并转义内部单引号。
     *
     * @param v 原始值字符串
     * @return EL 字面量
     */
    private String lit(String v) {
        return v != null && v.matches("-?\\d+(\\.\\d+)?") ? v : "'" + esc(v) + "'";
    }

    /**
     * 转义单引号（用于字符串字面量内部）。
     *
     * @param v 原始字符串
     * @return 转义后字符串
     */
    private String esc(String v) {
        return v == null ? "" : v.replace("'", "\\'");
    }
}
