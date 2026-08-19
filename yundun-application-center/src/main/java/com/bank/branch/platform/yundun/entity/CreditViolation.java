package com.bank.branch.platform.yundun.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 机构信贷风险责任认定及追究处理信息实体。 */
@Data
@TableName("t_credit_violation")
public class CreditViolation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String accountabilityCode;
    private String operatingMainResponsiblePersonResponsibility;
    private String clientName;
    private String iouNumber;
    private BigDecimal insurancePrincipal;
    private BigDecimal estimatedLoss;
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
    private String coefficientOfResponsibility;
    private String responsibilityRatio;
    private String isEconomicDeduction;
    private BigDecimal amountWithheld;
    private String specialAreaGeneralNegligenceExemption;
    private String generalNegligenceSmallAmountExemption;
    private String departmentSalaryLimitCondition;
    private BigDecimal recycleAndReturn;
    private String isFalseBuckle;
    private String otherProcessing;
    private String generalHandling;
    private String disciplinaryAction;
    private String processingType;
    @TableField("amount_withheld_2")
    private BigDecimal amountWithheld2;
    private String withholdingInstructions;
    private String nameAndNumberOfAccountabilityDocument;
    private LocalDateTime responsibilityDeterminationTime;
    private String whetherToLeave;
    private String isReportToTheBankingRegulatoryCommission;
    private LocalDateTime submissionTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "1", delval = "0")
    private Integer inUse;
}
