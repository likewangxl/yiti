package com.bank.branch.platform.bizapp.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过反射验证 QueryApi 接口方法参数名与文档契约(04-对外API契约)一致。
 * <p>
 * 项目根 pom.xml 已启用 {@code <parameters>true</parameters>}，
 * 编译后 class 文件中保留真实参数名，反射可直接读取。
 * </p>
 */
@DisplayName("QueryApi 参数名契约验证(反射)")
class QueryApiParamNameTest {

    @Test
    @DisplayName("LoanQueryApi.countCompletedByOrg 参数名必须为 orgId/startTime/endTime")
    void countCompletedByOrg_paramNames_matchesDoc() throws Exception {
        Method m = LoanQueryApi.class.getMethod("countCompletedByOrg",
                String.class, LocalDateTime.class, LocalDateTime.class);
        assertThat(m.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("orgId", "startTime", "endTime");
    }

    @Test
    @DisplayName("LoanQueryApi.sumCreditAmountByEmp 参数名必须为 empId/startTime/endTime")
    void sumCreditAmountByEmp_paramNames_matchesDoc() throws Exception {
        Method m = LoanQueryApi.class.getMethod("sumCreditAmountByEmp",
                String.class, LocalDateTime.class, LocalDateTime.class);
        assertThat(m.getParameters())
                .extracting(Parameter::getName)
                .containsExactly("empId", "startTime", "endTime");
    }

    @Test
    @DisplayName("SupportQueryApi.countCompletedByCreator/Assignee 参数名必须为 empId/startTime/endTime")
    void supportQueryApi_paramNames_matchesDoc() throws Exception {
        for (String method : new String[]{"countCompletedByCreator", "countCompletedByAssignee"}) {
            Method m = SupportQueryApi.class.getMethod(method,
                    String.class, LocalDateTime.class, LocalDateTime.class);
            assertThat(m.getParameters())
                    .as("%s 参数名", method)
                    .extracting(Parameter::getName)
                    .containsExactly("empId", "startTime", "endTime");
        }
    }
}
