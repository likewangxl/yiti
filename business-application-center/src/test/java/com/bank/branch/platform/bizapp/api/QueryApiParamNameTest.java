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

    /**
     * 前置守护：断言方法的参数名在运行时可读。
     * <p>
     * 若 maven-compiler-plugin 未配置 {@code <parameters>true</parameters>}，
     * 参数名将退化为 arg0/arg1/arg2，后续参数名断言会产生误导性的失败信息。
     * 本方法提前给出明确的失败提示。
     * </p>
     *
     * @param m 待检查的 Method
     */
    private void assertParametersPresent(Method m) {
        if (m.getParameters().length > 0) {
            assertThat(m.getParameters()[0].isNamePresent())
                    .as("编译时必须启用 -parameters(maven-compiler-plugin <parameters>true</parameters>)，"
                            + "否则参数名不可读 — 方法: %s", m.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("SupportQueryApi.countCompletedByCreator/Assignee 参数名必须为 empId/startTime/endTime")
    void supportQueryApi_paramNames_matchesDoc() throws Exception {
        for (String method : new String[]{"countCompletedByCreator", "countCompletedByAssignee"}) {
            Method m = SupportQueryApi.class.getMethod(method,
                    String.class, LocalDateTime.class, LocalDateTime.class);
            assertParametersPresent(m);
            assertThat(m.getParameters())
                    .as("%s 参数名", method)
                    .extracting(Parameter::getName)
                    .containsExactly("empId", "startTime", "endTime");
        }
    }
}
