package com.bank.branch.platform.bizapp.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 LoanApplyMapper.xml 中关键 SQL 的业务约束。
 * <p>
 * 采用字符串断言方式（Option A），直接加载 XML 资源文件，
 * 通过定位目标 {@code <select>} 块并断言必要子句是否存在，
 * 无需启动 Spring/MyBatis 上下文，执行速度极快。
 * </p>
 */
@DisplayName("LoanApplyMapper.xml SQL 约束验证")
class LoanApplyMapperXmlTest {

    private static final String XML_PATH =
            "src/main/resources/mapper/bizapp/LoanApplyMapper.xml";

    /**
     * sumCreditAmountByEmp SQL 必须只统计 COMPLETED 状态。
     * <p>
     * 背景：绩效统计若包含草稿/审批中状态的记录，数据会严重失真。
     * Mapper XML 应在 WHERE 子句中加 {@code AND status = 'COMPLETED'} 过滤。
     * </p>
     */
    @Test
    @DisplayName("sumCreditAmountByEmp SQL 必须只统计 COMPLETED 状态(绩效数据准确)")
    void sumCreditAmountByEmp_filtersOnlyCompleted() throws IOException {
        String xml = Files.readString(
                Paths.get(XML_PATH));

        // 定位 sumCreditAmountByEmp 代码块
        int startIdx = xml.indexOf("id=\"sumCreditAmountByEmp\"");
        assertThat(startIdx)
                .as("LoanApplyMapper.xml 中应存在 sumCreditAmountByEmp 查询")
                .isGreaterThan(-1);

        int endIdx = xml.indexOf("</select>", startIdx);
        assertThat(endIdx)
                .as("sumCreditAmountByEmp 查询块应有 </select> 结束标签")
                .isGreaterThan(startIdx);

        String block = xml.substring(startIdx, endIdx);

        // 断言 COMPLETED 过滤条件存在
        assertThat(block)
                .as("sumCreditAmountByEmp 的 WHERE 子句应包含 status 字段过滤(避免统计草稿/审批中数据)")
                .contains("status");
        assertThat(block)
                .as("sumCreditAmountByEmp 的 WHERE 子句应只统计 COMPLETED 状态(绩效数据准确性要求)")
                .contains("COMPLETED");
    }
}
