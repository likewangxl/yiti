package com.bank.branch.platform.yundun.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 信贷风险责任认定分页查询条件（排除主键及系统维护字段）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreditViolationQuery extends ViolationPageQuery {
    private String accountabilityCode;
    private String operatingMainResponsiblePersonResponsibility;
    private String clientName;
    private String iouNumber;
    private BigDecimal insurancePrincipalMin;
    private BigDecimal insurancePrincipalMax;
    private BigDecimal estimatedLossMin;
    private BigDecimal estimatedLossMax;
    private String isResponsibility;
    private String isAuditByHeadOffice;
    private String isMicrofinanceCreditBusiness;
    private String responsiblePersonName;
    private String employeeNumber;
    private String position;
    private String institutionName;
    private String institutionalLevel;
    private String affiliatedInstitution;
    private String responsibilityDetermination;
    private String subjectiveIntent;
    private String majorNegligence;
    private String generalNegligence;
    private BigDecimal coefficientOfResponsibilityMin;
    private BigDecimal coefficientOfResponsibilityMax;
    private BigDecimal responsibilityRatioMin;
    private BigDecimal responsibilityRatioMax;
    private String isEconomicDeduction;
    private BigDecimal amountWithheldMin;
    private BigDecimal amountWithheldMax;
    private String specialAreaGeneralNegligenceExemption;
    private String generalNegligenceSmallAmountExemption;
    private String departmentSalaryLimitCondition;
    private Boolean hasRecycleAndReturn;
    private String isFalseBuckle;
    private String otherProcessing;
    private String generalHandling;
    private String disciplinaryAction;
    private String processingType;
    private BigDecimal amountWithheld2Min;
    private BigDecimal amountWithheld2Max;
    private String withholdingInstructions;
    private String nameAndNumberOfAccountabilityDocument;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime responsibilityDeterminationTimeStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime responsibilityDeterminationTimeEnd;
    private String whetherToLeave;
    private String isReportToTheBankingRegulatoryCommission;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime submissionTimeStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime submissionTimeEnd;
    private String remark;
}
