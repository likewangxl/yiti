package com.bank.branch.platform.yundun.service;

import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.entity.AccountabilityViolation;
import com.bank.branch.platform.yundun.entity.CreditViolation;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 违规管理查询条件覆盖测试。 */
class ViolationQueryBuilderTest {

    private static final List<String> ACCOUNTABILITY_COLUMNS = List.of(
            "accountability_number", "accountability_source", "specific_source", "affiliated_institution",
            "violation_area_level_one", "violation_area_level_two", "reconsideration", "name", "work_number", "institution_name", "dept_name", "post_at_the_time",
            "accountability_positions", "gender", "role_classification", "type_of_person",
            "document_type", "id_number", "highest_education", "the_political_landscape",
            "is_dimission", "time_of_departure", "institutional_hierarchy",
            "attribution_of_violations", "business_area", "type_of_responsibility",
            "primary_and_secondary_responsibility", "circumstances_of_the_violation",
            "rank_at_the_time_of_the_violation", "accountability_ranks",
            "violation_characteristics", "areas_of_violation", "other_areas",
            "facts_of_the_violation", "party_attitude", "processing_basis",
            "accountability_documents_and_numbers", "penalty_time", "penalty_period",
            "penalty_release_time", "whether_to_submit_supervision", "submission_time",
            "general_handling", "disciplinary_action", "economic_treatment",
            "withholding_amount", "withholding_instructions", "remark"
    );

    private static final List<String> CREDIT_COLUMNS = List.of(
            "accountability_code", "operating_main_responsible_person_responsibility", "client_name", "iou_number", "insurance_principal",
            "estimated_loss", "is_responsibility", "is_audit_by_head_office",
            "is_microfinance_credit_business", "responsible_person_name", "employee_number",
            "position", "institution_name", "institutional_level", "affiliated_institution",
            "responsibility_determination", "subjective_intent", "major_negligence", "general_negligence", "coefficient_of_responsibility",
            "responsibility_ratio", "is_economic_deduction", "amount_withheld",
            "special_area_general_negligence_exemption", "general_negligence_small_amount_exemption", "department_salary_limit_condition",
            "recycle_and_return", "is_false_buckle", "other_processing", "general_handling",
            "disciplinary_action", "processing_type", "amount_withheld_2",
            "withholding_instructions", "name_and_number_of_accountability_document",
            "responsibility_determination_time", "whether_to_leave",
            "is_report_to_the_banking_regulatory_commission", "submission_time", "remark"
    );

    @Test
    void accountabilityQueryMustCoverEveryVisibleSearchField() throws Exception {
        AccountabilityViolationQuery query = filled(new AccountabilityViolationQuery());
        String sql = ViolationQueryBuilder.accountability(query).getSqlSegment().toLowerCase(Locale.ROOT);

        assertThat(ACCOUNTABILITY_COLUMNS).hasSize(48);
        assertThat(ACCOUNTABILITY_COLUMNS).allSatisfy(column -> assertThat(sql).contains(column));
        assertThat(sql).doesNotContain("accountability_for_violations_id");
        assertThat(sql).doesNotContain(" id ");
    }

    @Test
    void creditQueryMustCoverEveryVisibleSearchField() throws Exception {
        CreditViolationQuery query = filled(new CreditViolationQuery());
        String sql = ViolationQueryBuilder.credit(query).getSqlSegment().toLowerCase(Locale.ROOT);

        assertThat(CREDIT_COLUMNS).hasSize(40);
        assertThat(CREDIT_COLUMNS).allSatisfy(column -> assertThat(sql).contains(column));
        assertThat(sql).doesNotContain(" id ");
    }

    @Test
    void responsibilityCoefficientMustUseNumericRangeQuery() {
        CreditViolationQuery query = new CreditViolationQuery();
        query.setCoefficientOfResponsibilityMin(new BigDecimal("0.25"));
        query.setCoefficientOfResponsibilityMax(new BigDecimal("0.75"));

        String sql = ViolationQueryBuilder.credit(query).getSqlSegment().toLowerCase(Locale.ROOT);

        assertThat(sql).contains("coefficient_of_responsibility >=");
        assertThat(sql).contains("coefficient_of_responsibility <=");
        assertThat(sql).doesNotContain("coefficient_of_responsibility like");
    }

    @Test
    void responsibilityRatioMustUseNumericRangeQuery() {
        CreditViolationQuery query = new CreditViolationQuery();
        query.setResponsibilityRatioMin(new BigDecimal("0.25"));
        query.setResponsibilityRatioMax(new BigDecimal("0.75"));

        String sql = ViolationQueryBuilder.credit(query).getSqlSegment().toLowerCase(Locale.ROOT);

        assertThat(sql).contains("responsibility_ratio >=");
        assertThat(sql).contains("responsibility_ratio <=");
        assertThat(sql).doesNotContain("responsibility_ratio like");
    }

    @Test
    void recycleAndReturnPresenceMustQueryNullability() {
        CreditViolationQuery presentQuery = new CreditViolationQuery();
        presentQuery.setHasRecycleAndReturn(true);
        CreditViolationQuery absentQuery = new CreditViolationQuery();
        absentQuery.setHasRecycleAndReturn(false);

        String presentSql = ViolationQueryBuilder.credit(presentQuery).getSqlSegment().toLowerCase(Locale.ROOT);
        String absentSql = ViolationQueryBuilder.credit(absentQuery).getSqlSegment().toLowerCase(Locale.ROOT);

        assertThat(presentSql).contains("recycle_and_return is not null");
        assertThat(absentSql).contains("recycle_and_return is null");
    }

    @Test
    void creditMultiValueFiltersMustUseTrimmedDeduplicatedExactInAndAndAcrossFields() {
        CreditViolationQuery query = new CreditViolationQuery();
        query.setClientName(" 张三，李四, 张三 ; ;\n 王五 ");
        query.setResponsiblePersonName(" Alice；Bob; Alice ");
        query.setEmployeeNumber(" 001\n002,001 ");

        var wrapper = ViolationQueryBuilder.credit(query);
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);

        assertThat(sql).contains("client_name in");
        assertThat(sql).contains("responsible_person_name in");
        assertThat(sql).contains("employee_number in");
        assertThat(sql).doesNotContain("client_name like");
        assertThat(sql).doesNotContain("responsible_person_name like");
        assertThat(sql).doesNotContain("employee_number like");
        assertThat(sql.indexOf("client_name in")).isLessThan(sql.indexOf("responsible_person_name in"));
        assertThat(sql.indexOf("responsible_person_name in")).isLessThan(sql.indexOf("employee_number in"));
        assertThat(wrapper.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder("张三", "李四", "王五", "Alice", "Bob", "001", "002");
    }

    @Test
    void accountabilityNameAndWorkNumberFiltersMustUseTrimmedDeduplicatedExactIn() {
        AccountabilityViolationQuery query = new AccountabilityViolationQuery();
        query.setName(" 张三，李四, 张三 ; ;\n 王五 ");
        query.setWorkNumber(" 001\n002,001 ");

        var wrapper = ViolationQueryBuilder.accountability(query);
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);

        assertThat(sql).contains("name in");
        assertThat(sql).contains("work_number in");
        assertThat(sql).doesNotContain("name like");
        assertThat(sql).doesNotContain("work_number like");
        assertThat(wrapper.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder("张三", "李四", "王五", "001", "002");
    }

    @Test
    void creditMultiValueFiltersMustRejectMoreThanOneHundredValuesWithoutSilentlyDroppingThem() {
        String values = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> "value-" + index)
                .reduce((left, right) -> left + "，" + right)
                .orElseThrow();
        CreditViolationQuery query = new CreditViolationQuery();
        query.setClientName(" ;\n，" + values + "； ");

        assertThatThrownBy(() -> ViolationQueryBuilder.credit(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientName")
                .hasMessageContaining("100");
    }

    @Test
    void inUseMustBeConfiguredAsLogicalDeleteFlagForBothTables() throws Exception {
        assertLogicalDelete(AccountabilityViolation.class);
        assertLogicalDelete(CreditViolation.class);
    }

    @Test
    void creditSecondWithholdingAmountMustMapToPhysicalColumnWithNumericSuffix() throws Exception {
        Field amountWithheld2 = CreditViolation.class.getDeclaredField("amountWithheld2");
        TableField tableField = amountWithheld2.getAnnotation(TableField.class);

        assertThat(tableField).isNotNull();
        assertThat(tableField.value()).isEqualTo("amount_withheld_2");
    }

    private void assertLogicalDelete(Class<?> entityType) throws Exception {
        Field inUse = entityType.getDeclaredField("inUse");
        TableLogic tableLogic = inUse.getAnnotation(TableLogic.class);

        assertThat(tableLogic).isNotNull();
        assertThat(tableLogic.value()).isEqualTo("1");
        assertThat(tableLogic.delval()).isEqualTo("0");
    }

    private <T> T filled(T target) throws IllegalAccessException {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == String.class) {
                    field.set(target, "测试");
                } else if (field.getType() == Integer.class || field.getType() == int.class) {
                    field.set(target, 1);
                } else if (field.getType() == BigDecimal.class) {
                    field.set(target, BigDecimal.ONE);
                } else if (field.getType() == Boolean.class || field.getType() == boolean.class) {
                    field.set(target, true);
                } else if (field.getType() == LocalDateTime.class) {
                    field.set(target, LocalDateTime.of(2026, 8, 12, 9, 0));
                }
            }
            type = type.getSuperclass();
        }
        return target;
    }
}
