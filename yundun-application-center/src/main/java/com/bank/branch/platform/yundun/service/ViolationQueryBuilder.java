package com.bank.branch.platform.yundun.service;

import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.entity.AccountabilityViolation;
import com.bank.branch.platform.yundun.entity.CreditViolation;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 两张违规源表的查询条件构造器。
 *
 * <p>列名全部由服务端常量声明，前端参数从不参与 SQL 列名拼接。</p>
 */
public final class ViolationQueryBuilder {

    private static final int MAX_MULTI_VALUE_COUNT = 100;
    private static final Pattern MULTI_VALUE_SEPARATOR = Pattern.compile("[，,；;\\r\\n]+");

    private ViolationQueryBuilder() {
    }

    /** 构造人员违规问责查询条件。 */
    public static QueryWrapper<AccountabilityViolation> accountability(AccountabilityViolationQuery q) {
        QueryWrapper<AccountabilityViolation> w = new QueryWrapper<>();
        like(w, "accountability_number", q.getAccountabilityNumber());
        like(w, "accountability_source", q.getAccountabilitySource());
        like(w, "specific_source", q.getSpecificSource());
        like(w, "affiliated_institution", q.getAffiliatedInstitution());
        like(w, "violation_area_level_one", q.getViolationAreaLevelOne());
        like(w, "violation_area_level_two", q.getViolationAreaLevelTwo());
        like(w, "reconsideration", q.getReconsideration());
        in(w, "name", "name", q.getName());
        in(w, "work_number", "workNumber", q.getWorkNumber());
        like(w, "institution_name", q.getInstitutionName());
        like(w, "dept_name", q.getDeptName());
        like(w, "post_at_the_time", q.getPostAtTheTime());
        like(w, "accountability_positions", q.getAccountabilityPositions());
        eq(w, "gender", q.getGender());
        eq(w, "role_classification", q.getRoleClassification());
        eq(w, "type_of_person", q.getTypeOfPerson());
        eq(w, "document_type", q.getDocumentType());
        like(w, "id_number", q.getIdNumber());
        eq(w, "highest_education", q.getHighestEducation());
        eq(w, "the_political_landscape", q.getThePoliticalLandscape());
        eq(w, "is_dimission", q.getIsDimission());
        range(w, "time_of_departure", q.getTimeOfDepartureStart(), q.getTimeOfDepartureEnd());
        eq(w, "institutional_hierarchy", q.getInstitutionalHierarchy());
        eq(w, "attribution_of_violations", q.getAttributionOfViolations());
        like(w, "business_area", q.getBusinessArea());
        eq(w, "type_of_responsibility", q.getTypeOfResponsibility());
        eq(w, "primary_and_secondary_responsibility", q.getPrimaryAndSecondaryResponsibility());
        eq(w, "circumstances_of_the_violation", q.getCircumstancesOfTheViolation());
        like(w, "rank_at_the_time_of_the_violation", q.getRankAtTheTimeOfTheViolation());
        like(w, "accountability_ranks", q.getAccountabilityRanks());
        eq(w, "violation_characteristics", q.getViolationCharacteristics());
        eq(w, "areas_of_violation", q.getAreasOfViolation());
        like(w, "other_areas", q.getOtherAreas());
        like(w, "facts_of_the_violation", q.getFactsOfTheViolation());
        like(w, "party_attitude", q.getPartyAttitude());
        like(w, "processing_basis", q.getProcessingBasis());
        like(w, "accountability_documents_and_numbers", q.getAccountabilityDocumentsAndNumbers());
        range(w, "penalty_time", q.getPenaltyTimeStart(), q.getPenaltyTimeEnd());
        like(w, "penalty_period", q.getPenaltyPeriod());
        range(w, "penalty_release_time", q.getPenaltyReleaseTimeStart(), q.getPenaltyReleaseTimeEnd());
        eq(w, "whether_to_submit_supervision", q.getWhetherToSubmitSupervision());
        range(w, "submission_time", q.getSubmissionTimeStart(), q.getSubmissionTimeEnd());
        eq(w, "general_handling", q.getGeneralHandling());
        eq(w, "disciplinary_action", q.getDisciplinaryAction());
        like(w, "economic_treatment", q.getEconomicTreatment());
        range(w, "withholding_amount", q.getWithholdingAmountMin(), q.getWithholdingAmountMax());
        like(w, "withholding_instructions", q.getWithholdingInstructions());
        like(w, "remark", q.getRemark());
        return w.orderByDesc("create_time").orderByDesc("id");
    }

    /** 构造信贷风险责任认定查询条件。 */
    public static QueryWrapper<CreditViolation> credit(CreditViolationQuery q) {
        QueryWrapper<CreditViolation> w = new QueryWrapper<>();
        like(w, "accountability_code", q.getAccountabilityCode());
        like(w, "operating_main_responsible_person_responsibility",
                q.getOperatingMainResponsiblePersonResponsibility());
        in(w, "client_name", "clientName", q.getClientName());
        like(w, "iou_number", q.getIouNumber());
        range(w, "insurance_principal", q.getInsurancePrincipalMin(), q.getInsurancePrincipalMax());
        range(w, "estimated_loss", q.getEstimatedLossMin(), q.getEstimatedLossMax());
        eq(w, "is_responsibility", q.getIsResponsibility());
        eq(w, "is_audit_by_head_office", q.getIsAuditByHeadOffice());
        eq(w, "is_microfinance_credit_business", q.getIsMicrofinanceCreditBusiness());
        in(w, "responsible_person_name", "responsiblePersonName", q.getResponsiblePersonName());
        in(w, "employee_number", "employeeNumber", q.getEmployeeNumber());
        eq(w, "position", q.getPosition());
        like(w, "institution_name", q.getInstitutionName());
        eq(w, "institutional_level", q.getInstitutionalLevel());
        like(w, "affiliated_institution", q.getAffiliatedInstitution());
        eq(w, "responsibility_determination", q.getResponsibilityDetermination());
        like(w, "subjective_intent", q.getSubjectiveIntent());
        like(w, "major_negligence", q.getMajorNegligence());
        like(w, "general_negligence", q.getGeneralNegligence());
        range(w, "coefficient_of_responsibility", q.getCoefficientOfResponsibilityMin(),
                q.getCoefficientOfResponsibilityMax());
        range(w, "responsibility_ratio", q.getResponsibilityRatioMin(), q.getResponsibilityRatioMax());
        eq(w, "is_economic_deduction", q.getIsEconomicDeduction());
        range(w, "amount_withheld", q.getAmountWithheldMin(), q.getAmountWithheldMax());
        like(w, "special_area_general_negligence_exemption", q.getSpecialAreaGeneralNegligenceExemption());
        like(w, "general_negligence_small_amount_exemption", q.getGeneralNegligenceSmallAmountExemption());
        like(w, "department_salary_limit_condition", q.getDepartmentSalaryLimitCondition());
        presence(w, "recycle_and_return", q.getHasRecycleAndReturn());
        eq(w, "is_false_buckle", q.getIsFalseBuckle());
        eq(w, "other_processing", q.getOtherProcessing());
        eq(w, "general_handling", q.getGeneralHandling());
        eq(w, "disciplinary_action", q.getDisciplinaryAction());
        eq(w, "processing_type", q.getProcessingType());
        range(w, "amount_withheld_2", q.getAmountWithheld2Min(), q.getAmountWithheld2Max());
        like(w, "withholding_instructions", q.getWithholdingInstructions());
        like(w, "name_and_number_of_accountability_document", q.getNameAndNumberOfAccountabilityDocument());
        range(w, "responsibility_determination_time", q.getResponsibilityDeterminationTimeStart(),
                q.getResponsibilityDeterminationTimeEnd());
        eq(w, "whether_to_leave", q.getWhetherToLeave());
        eq(w, "is_report_to_the_banking_regulatory_commission",
                q.getIsReportToTheBankingRegulatoryCommission());
        range(w, "submission_time", q.getSubmissionTimeStart(), q.getSubmissionTimeEnd());
        like(w, "remark", q.getRemark());
        return w.orderByDesc("create_time").orderByDesc("id");
    }

    private static <T> void like(QueryWrapper<T> w, String column, String value) {
        if (value != null && !value.isBlank()) {
            w.like(column, value.trim());
        }
    }

    /** 将允许多值输入的查询字段转换为去重后的精确匹配条件。 */
    private static <T> void in(QueryWrapper<T> w, String column, String fieldName, String value) {
        List<String> values = splitMultiValue(value);
        if (values.size() > MAX_MULTI_VALUE_COUNT) {
            throw new IllegalArgumentException(fieldName + " 最多支持 " + MAX_MULTI_VALUE_COUNT + " 个查询值");
        }
        if (!values.isEmpty()) {
            w.in(column, values);
        }
    }

    private static List<String> splitMultiValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return MULTI_VALUE_SEPARATOR.splitAsStream(value)
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .distinct()
                .toList();
    }

    private static <T> void eq(QueryWrapper<T> w, String column, String value) {
        if (value != null && !value.isBlank()) {
            w.eq(column, value.trim());
        }
    }

    private static <T> void presence(QueryWrapper<T> w, String column, Boolean present) {
        if (Boolean.TRUE.equals(present)) {
            w.isNotNull(column);
        } else if (Boolean.FALSE.equals(present)) {
            w.isNull(column);
        }
    }

    private static <T> void range(QueryWrapper<T> w, String column, BigDecimal min, BigDecimal max) {
        if (min != null) {
            w.ge(column, min);
        }
        if (max != null) {
            w.le(column, max);
        }
    }

    private static <T> void range(QueryWrapper<T> w, String column, LocalDateTime start, LocalDateTime end) {
        if (start != null) {
            w.ge(column, start);
        }
        if (end != null) {
            w.le(column, end);
        }
    }
}
