package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.performance.controller.dto.AddKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.CreateKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.PublishKpiSchemeReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiItemReqDTO;
import com.bank.branch.platform.performance.controller.dto.UpdateKpiSchemeReqDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KPI 方案请求 DTO 的 JSR-303 校验 UT.
 */
class KpiSchemeRequestDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    @Test
    void createKpiSchemeReq_whenSchemeCodeLowercase_shouldPass() {
        // schemeCode 不再强约束大写格式（仅 @NotBlank + @Size≤64），小写/下划线均可，调用方自证唯一。
        CreateKpiSchemeReqDTO req = new CreateKpiSchemeReqDTO();
        req.setSchemeCode("lower_case");
        req.setSchemeName("n");
        req.setCycleType("MONTHLY");
        req.setOpenDetail(Boolean.FALSE);

        Set<ConstraintViolation<CreateKpiSchemeReqDTO>> violations = validator.validateProperty(req, "schemeCode");

        assertThat(violations).isEmpty();
    }

    @Test
    void createKpiSchemeReq_whenCycleTypeInvalid_shouldViolation() {
        CreateKpiSchemeReqDTO req = new CreateKpiSchemeReqDTO();
        req.setCycleType("WEEKLY");

        Set<ConstraintViolation<CreateKpiSchemeReqDTO>> violations = validator.validateProperty(req, "cycleType");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("cycleType");
    }

    @Test
    void createKpiSchemeReq_whenOpenDetailNull_shouldViolation() {
        CreateKpiSchemeReqDTO req = new CreateKpiSchemeReqDTO();
        req.setOpenDetail(null);

        Set<ConstraintViolation<CreateKpiSchemeReqDTO>> violations = validator.validateProperty(req, "openDetail");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("openDetail");
    }

    @Test
    void updateKpiSchemeReq_whenAllNull_shouldNotViolate() {
        UpdateKpiSchemeReqDTO req = new UpdateKpiSchemeReqDTO();

        Set<ConstraintViolation<UpdateKpiSchemeReqDTO>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void updateKpiSchemeReq_whenCycleTypeInvalid_shouldViolation() {
        UpdateKpiSchemeReqDTO req = new UpdateKpiSchemeReqDTO();
        req.setCycleType("DAILY");

        Set<ConstraintViolation<UpdateKpiSchemeReqDTO>> violations = validator.validate(req);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("cycleType");
    }

    @Test
    void addKpiItemReq_whenWeightNegative_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("METRIC_OK");
        req.setWeight(new BigDecimal("-1"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "weight");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("weight");
    }

    @Test
    void addKpiItemReq_whenWeightOverHundred_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("METRIC_OK");
        req.setWeight(new BigDecimal("200"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "weight");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("weight");
    }

    @Test
    void addKpiItemReq_whenMetricCodeBlank_shouldViolation() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode(" ");
        req.setWeight(new BigDecimal("10"));

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "metricCode");

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("metricCode");
    }

    @Test
    void addKpiItemReq_whenFormulaBlank_shouldPass() {
        // 计分公式改为可空（前端已取消计分公式列，仅保留 @Size≤500）：留空 / 纯空格不再触发校验。
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("M_X");
        req.setWeight(new BigDecimal("10"));
        req.setFormula("  ");

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "formula");

        assertThat(violations).isEmpty();
    }

    @Test
    void addKpiItemReq_whenFormulaPresent_shouldPass() {
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setFormula("min(actual / target * weight, 120)");

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validateProperty(req, "formula");

        assertThat(violations).isEmpty();
    }

    @Test
    void addKpiItemReq_whenMetricCodeDigitStart_shouldNotViolateOnPattern() {
        // KPI 加项是"引用既有指标"，不做编码格式校验（格式校验只在新建指标 CreateMetricReqDTO 处）。
        // 数字/下划线/小写开头的既有指标编码（如 0602001、99）都应能被引用。
        AddKpiItemReqDTO req = new AddKpiItemReqDTO();
        req.setMetricCode("0602001");
        req.setWeight(new BigDecimal("10"));
        req.setFormula("actual / target * 100");

        Set<ConstraintViolation<AddKpiItemReqDTO>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void updateKpiItemReq_whenAllNull_shouldNotViolate() {
        UpdateKpiItemReqDTO req = new UpdateKpiItemReqDTO();

        Set<ConstraintViolation<UpdateKpiItemReqDTO>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    @Test
    void publishKpiSchemeReq_whenReasonBlank_shouldViolation() {
        PublishKpiSchemeReqDTO req = new PublishKpiSchemeReqDTO();
        req.setReason(" ");

        Set<ConstraintViolation<PublishKpiSchemeReqDTO>> violations = validator.validate(req);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("reason");
    }
}
