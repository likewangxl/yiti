package com.bank.branch.platform.yundun.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.yundun.service.AccountabilityViolationService;
import com.bank.branch.platform.yundun.service.CreditViolationService;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 导入模板下载接口契约。 */
class ViolationImportTemplateControllerTest {

    @Test
    void accountabilityTemplateShouldContainOnlyCurrentImportColumns() throws Exception {
        AccountabilityViolationController controller = new AccountabilityViolationController(mock(AccountabilityViolationService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.downloadImportTemplate(response);

        List<Map<Integer, String>> rows = readRows(response);
        assertThat(response.getContentType()).startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(decodedAttachmentFilename(response)).contains("人员违规信息导入模板");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).values()).contains("员工工号(*)", "违规事实发生时职务、岗位(*)");
        assertThat(rows.get(0).values()).doesNotContain("部门名称", "角色分类", "业务领域");
    }

    @Test
    void creditTemplateShouldKeepFourHeaderRowsAndExcludeLegacyColumns() throws Exception {
        CreditViolationController controller = new CreditViolationController(mock(CreditViolationService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.downloadImportTemplate(response);

        List<Map<Integer, String>> rows = readRows(response);
        assertThat(decodedAttachmentFilename(response)).contains("信贷风险信息导入模板");
        assertCreditHeaderRows(rows);
        assertThat(rows.stream().flatMap(row -> row.values().stream()))
                .doesNotContain("是否免于经济扣发", "回收返还（元）");
    }

    @Test
    void creditExportShouldKeepTheSameFourHeaderRows() throws Exception {
        CreditViolationService service = mock(CreditViolationService.class);
        when(service.exportRows(any(CreditViolationQuery.class), isNull())).thenReturn(List.of());
        CreditViolationController controller = new CreditViolationController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(new CreditViolationQuery(), null, response);

        assertThat(decodedAttachmentFilename(response)).contains("信贷风险信息_");
        assertCreditHeaderRows(readRows(response));
    }

    private static void assertCreditHeaderRows(List<Map<Integer, String>> rows) {
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).values())
                .as("第1行仅保留参考文件的不可见占位符，不应承载业务分组")
                .doesNotContain("责任认定对象所在机构", "责任认定", "经济扣发", "违规问责");
        assertThat(rows.get(1).values())
                .as("第2行应承载信贷模板的业务分组")
                .contains("责任认定对象所在机构", "责任认定", "经济扣发", "违规问责");
        assertThat(rows.get(2).values()).contains("机构名称", "经济处理");
        assertThat(rows.get(3).values()).contains("处理类型", "扣发金额（元）", "扣发说明");
        assertThat(rows.get(1).values()).contains(
                "员工工号", "核销金额(万元)");
        assertThat(rows.get(2).values()).contains(
                "机构名称", "机构层级", "辖属机构", "主观故意", "重大过失", "一般过失", "责任系数", "责任占比",
                "是否承担经营主责任人责任", "扣发金额（元）",
                "是否为专项领域、一般过失且占比10%以下免于经济处理的情形",
                "是否为一般过失且扣减金额在500元（含）以下免于经济处理的情形",
                "是否为经济扣减以部门前三年度薪酬为限的情形", "一般处理", "纪律处分",
                "问责文件名称及文号");
    }

    private static List<Map<Integer, String>> readRows(MockHttpServletResponse response) {
        return EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .headRowNumber(0)
                .sheet()
                .doReadSync();
    }

    private static String decodedAttachmentFilename(MockHttpServletResponse response) {
        String attachment = response.getHeader("Content-Disposition");
        return URLDecoder.decode(attachment.substring(attachment.indexOf('=') + 1), StandardCharsets.UTF_8);
    }
}
