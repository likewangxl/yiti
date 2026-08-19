package com.bank.branch.platform.yundun.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationSaveReq;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.excel.CreditViolationExcelWriteHandler;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最新云盾 Excel 模板的列、必填标记及多级表头契约。
 *
 * <p>测试直接读取导出行模型的 EasyExcel 元数据，并在内存中生成/解析工作簿；不连接数据库。
 * 这些断言刻意先于模板实现落地，缺少最新列或四行表头时应稳定失败。</p>
 */
class ViolationTemplateContractTest {

    private static final List<String> ACCOUNTABILITY_TEMPLATE_COLUMNS = List.of(
            "主键(不可更改)", "问责编号", "机构名称", "问责来源", "具体来源", "机构层级", "辖属机构", "所属条线",
            "被处罚人员名称", "员工工号", "性别", "人员类型", "违规事实发生时职务、岗位", "被问责时岗位、职务",
            "证件类型", "证件号码", "最高学历", "政治面貌", "是否离职", "离职时间", "责任类型", "违规特性",
            "违规领域（旧）", "违规领域（一级）", "违规领域（二级）", "违规事实", "处理依据", "问责文件及文号",
            "处罚时间", "一般处理", "纪律处分", "经济处理方式", "扣发金额", "扣发说明（落实情况）", "复议", "处罚期限",
            "处罚解除时间", "是否报送监管", "报送时间", "备注");

    private static final List<String> CREDIT_TEMPLATE_COLUMNS = List.of(
            "问责代码", "客户名称", "借据号", "出险本金(万元)", "核销金额(万元)", "认定结果有无责任(有/无)", "是否属于总行审核",
            "是否属于普惠金融信贷业务", "责任认定对象（姓名）", "员工工号", "职务(岗位)", "是否离职", "机构名称", "机构层级",
            "辖属机构", "主观故意", "重大过失", "一般过失", "责任系数", "责任占比", "是否承担经营主责任人责任", "扣发金额（元）",
            "是否为专项领域、一般过失且占比10%以下免于经济处理的情形", "是否为一般过失且扣减金额在500元（含）以下免于经济处理的情形",
            "是否为经济扣减以部门前三年度薪酬为限的情形", "一般处理", "纪律处分", "处理类型", "扣发金额（元）", "扣发说明", "问责文件名称及文号");

    private static final List<List<String>> CREDIT_ECONOMIC_PROCESSING_HEAD = List.of(
            List.of("", "违规问责", "经济处理", "处理类型"),
            List.of("", "违规问责", "经济处理", "扣发金额（元）"),
            List.of("", "违规问责", "经济处理", "扣发说明"));

    @Test
    void accountabilityExportMustPutTemplateColumnsBeforeLegacyColumns() {
        LinkedHashMap<String, List<String>> headers = excelHeaders(AccountabilityViolationSaveReq.class);
        List<String> leaves = leaves(headers);

        assertThat(normalizedLeaves(leaves))
                .as("人员违规导出应按导入模板列顺序，旧列随后追加")
                .startsWith(ACCOUNTABILITY_TEMPLATE_COLUMNS.toArray(String[]::new));
        assertThat(normalizedLeaves(leaves))
                .containsSubsequence("备注", "部门名称", "角色分类", "业务领域", "主、次要责任");
    }

    @Test
    void accountabilityTemplateMustShowRequiredMarkersWithoutAddingBeanValidation() {
        LinkedHashMap<String, List<String>> headers = excelHeaders(AccountabilityViolationSaveReq.class);

        assertThat(headers.get("workNumber"))
                .as("人员模板应保留员工工号提示星号")
                .containsExactly("员工工号(*)");
        assertThat(normalizedLeaves(leaves(headers)))
                .contains("问责编号", "问责来源", "具体来源", "辖属机构", "违规领域（一级）", "违规领域（二级）");

        List<String> notBlankFields = Arrays.stream(AccountabilityViolationSaveReq.class.getDeclaredFields())
                .filter(field -> field.getAnnotation(NotBlank.class) != null)
                .map(Field::getName)
                .toList();
        assertThat(notBlankFields)
                .as("人员新增/编辑请求只能在员工工号上施加必填约束")
                .containsExactly("workNumber");
    }

    @Test
    void creditExportMustExposeLatestColumnsAndReplaceEstimatedLossBusinessTitle() {
        LinkedHashMap<String, List<String>> headers = excelHeaders(CreditViolationSaveReq.class);
        List<String> leaves = normalizedLeaves(leaves(headers));

        assertThat(leaves)
                .as("信贷最新模板应包含经营主责任人责任列")
                .contains("是否承担经营主责任人责任");
        assertThat(leaves)
                .as("estimatedLoss 的导出业务标题应替换为核销金额")
                .anyMatch(header -> header.contains("核销金额"));
        assertThat(leaves)
                .as("最新信贷模板不应继续暴露预估损失额业务标题")
                .noneMatch(header -> header.contains("预估损失额"));
        assertThat(leaves)
                .as("信贷导出应先按导入模板列顺序输出，再追加历史列")
                .startsWith(CREDIT_TEMPLATE_COLUMNS.toArray(String[]::new))
                .containsSubsequence("问责文件名称及文号", "责任认定", "是否免于经济扣发", "回收返还（元）");
    }

    @Test
    void creditEconomicProcessingColumnsMustUseFourLevelHeaders() {
        LinkedHashMap<String, List<String>> headers = excelHeaders(CreditViolationSaveReq.class);

        assertThat(headers.values())
                .as("信贷违规问责三项列必须在第二层分组、第三层经济处理下展示明细")
                .containsAll(CREDIT_ECONOMIC_PROCESSING_HEAD);
    }

    @Test
    void creditTemplateMustGenerateAndParseFourHeaderRows() {
        CreditViolationSaveReq source = new CreditViolationSaveReq();
        source.setEmployeeNumber("E-TEMPLATE-001");
        source.setProcessingType("扣发绩效收入(一次性)");
        source.setAmountWithheld2(new BigDecimal("12.50"));
        source.setWithholdingInstructions("模板解析校验");

        byte[] workbook = writeCreditWorkbook(source);
        List<Map<Integer, String>> rawRows = EasyExcel.read(new ByteArrayInputStream(workbook))
                .headRowNumber(0)
                .sheet()
                .doReadSync();

        assertThat(rawRows)
                .as("四层物理表头加一行数据应生成至少五行")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(rawRows.get(0).values()).doesNotContain("经济扣发", "违规问责");
        assertThat(rawRows.get(1).values())
                .contains("责任认定对象所在机构", "责任认定", "经济扣发", "违规问责");
        assertThat(rawRows.get(2).values())
                .contains("经济处理");
        assertThat(rawRows.get(3).values())
                .contains("处理类型", "扣发金额（元）", "扣发说明");

        List<CreditViolationSaveReq> parsed = EasyExcel.read(new ByteArrayInputStream(workbook))
                .head(CreditViolationSaveReq.class)
                .headRowNumber(4)
                .sheet()
                .doReadSync();
        assertThat(parsed).singleElement().satisfies(row -> {
            assertThat(row.getEmployeeNumber()).isEqualTo("E-TEMPLATE-001");
            assertThat(row.getProcessingType()).isEqualTo("扣发绩效收入(一次性)");
            assertThat(row.getAmountWithheld2()).isEqualByComparingTo("12.50");
            assertThat(row.getWithholdingInstructions()).isEqualTo("模板解析校验");
        });
    }

    @Test
    void creditTemplateMustKeepEmployeeNumberAsOnlySaveRequestRequiredField() {
        List<String> notBlankFields = Arrays.stream(CreditViolationSaveReq.class.getDeclaredFields())
                .filter(field -> field.getAnnotation(NotBlank.class) != null)
                .map(Field::getName)
                .toList();

        assertThat(notBlankFields)
                .as("信贷新增/编辑请求的必填规则应可由员工工号唯一承担")
                .containsExactly("employeeNumber");
    }

    private static byte[] writeCreditWorkbook(CreditViolationSaveReq row) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output, CreditViolationSaveReq.class)
                .registerWriteHandler(new CreditViolationExcelWriteHandler(true))
                .sheet("信贷风险信息")
                .doWrite(List.of(row));
        return output.toByteArray();
    }

    private static LinkedHashMap<String, List<String>> excelHeaders(Class<?> rowType) {
        LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();
        for (Field field : rowType.getDeclaredFields()) {
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            if (property != null) {
                headers.put(field.getName(), List.of(property.value()));
            }
        }
        return headers;
    }

    private static List<String> leaves(Map<String, List<String>> headers) {
        return headers.values().stream().map(ViolationTemplateContractTest::leaf).toList();
    }

    private static List<String> normalizedLeaves(List<String> headers) {
        return headers.stream().map(ViolationTemplateContractTest::withoutRequiredMarker).toList();
    }

    private static String leaf(List<String> path) {
        for (int index = path.size() - 1; index >= 0; index--) {
            if (!path.get(index).isBlank()) {
                return path.get(index);
            }
        }
        return "";
    }

    private static String withoutRequiredMarker(String header) {
        return header == null ? null : header.replaceFirst("\\s*(?:\\(\\*\\)|\\*)$", "");
    }
}
