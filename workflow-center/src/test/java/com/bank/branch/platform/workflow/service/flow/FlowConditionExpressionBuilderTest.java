package com.bank.branch.platform.workflow.service.flow;

import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * FlowConditionExpressionBuilder 单元测试（纯 JUnit5，无 Spring 上下文）
 */
class FlowConditionExpressionBuilderTest {

    private FlowConditionExpressionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new FlowConditionExpressionBuilder();
    }

    // ----------------------------------------------------------------
    // 辅助方法
    // ----------------------------------------------------------------

    private FlowConditionDTO dto(String logic, FlowConditionDTO.Cond... conds) {
        FlowConditionDTO d = new FlowConditionDTO();
        d.setLogic(logic);
        d.setConditions(List.of(conds));
        return d;
    }

    private FlowConditionDTO.Cond cond(String field, String op, String value) {
        return new FlowConditionDTO.Cond(field, op, value);
    }

    // ----------------------------------------------------------------
    // 测试用例
    // ----------------------------------------------------------------

    /** single_EQ：单条件 AND，字符串值加引号 */
    @Test
    void single_EQ() {
        String el = builder.toEl(dto("AND", cond("bizKind", "EQ", "CORP")));
        assertThat(el).isEqualTo("${bizKind == 'CORP'}");
    }

    /** multi_AND：多条件 AND 用 " && " 连接 */
    @Test
    void multi_AND() {
        String el = builder.toEl(dto("AND",
                cond("bizKind", "EQ", "CORP"),
                cond("custType", "NE", "RETAIL")));
        assertThat(el).isEqualTo("${bizKind == 'CORP' && custType != 'RETAIL'}");
    }

    /** multi_OR：多条件 OR 用 " || " 连接 */
    @Test
    void multi_OR() {
        String el = builder.toEl(dto("OR",
                cond("bizKind", "EQ", "CORP"),
                cond("custType", "NE", "RETAIL")));
        assertThat(el).isEqualTo("${bizKind == 'CORP' || custType != 'RETAIL'}");
    }

    /** in_op：IN 操作符，拆逗号后每项用 == ，用 || 连，外包括号 */
    @Test
    void in_op() {
        String el = builder.toEl(dto("AND", cond("bizKind", "IN", "CORP,PER")));
        assertThat(el).isEqualTo("${(bizKind == 'CORP' || bizKind == 'PER')}");
    }

    /** not_in_op：NOT_IN 操作符，拆逗号后每项用 != ，用 && 连，外包括号 */
    @Test
    void not_in_op() {
        String el = builder.toEl(dto("AND", cond("bizKind", "NOT_IN", "CORP,PER")));
        assertThat(el).isEqualTo("${(bizKind != 'CORP' && bizKind != 'PER')}");
    }

    /** numeric_GT_unquoted：数字值 GT 不加引号 */
    @Test
    void numeric_GT_unquoted() {
        String el = builder.toEl(dto("AND", cond("amount", "GT", "100")));
        assertThat(el).isEqualTo("${amount > 100}");
    }

    /** ge_le_lt：GE/LE/LT 运算符映射正确 */
    @Test
    void ge_le_lt() {
        assertThat(builder.toEl(dto("AND", cond("amount", "GE", "50")))).isEqualTo("${amount >= 50}");
        assertThat(builder.toEl(dto("AND", cond("amount", "LE", "200")))).isEqualTo("${amount <= 200}");
        assertThat(builder.toEl(dto("AND", cond("amount", "LT", "300")))).isEqualTo("${amount < 300}");
    }

    /** contains_op：CONTAINS 生成 field.contains('value') */
    @Test
    void contains_op() {
        String el = builder.toEl(dto("AND", cond("name", "CONTAINS", "X")));
        assertThat(el).isEqualTo("${name.contains('X')}");
    }

    /** null_or_empty_returnsNull：入参 null 或 conditions 为空 → 返回 null */
    @Test
    void null_or_empty_returnsNull() {
        assertThat(builder.toEl(null)).isNull();

        FlowConditionDTO empty = new FlowConditionDTO();
        empty.setLogic("AND");
        empty.setConditions(List.of());
        assertThat(builder.toEl(empty)).isNull();
    }

    /** rejects_unknown_op：不支持的运算符 → 抛 IllegalArgumentException */
    @Test
    void rejects_unknown_op() {
        assertThatThrownBy(() -> builder.toEl(dto("AND", cond("bizKind", "BAD", "X"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BAD");
    }
}
