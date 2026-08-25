package com.bank.branch.platform.yundun.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 人员违规问责分页查询条件（排除主键及系统维护字段）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountabilityViolationQuery extends ViolationPageQuery {
    private String accountabilityNumber;
    private String accountabilitySource;
    private String specificSource;
    private String affiliatedInstitution;
    private String violationAreaLevelOne;
    private String violationAreaLevelTwo;
    private String reconsideration;
    private String name;
    private String workNumber;
    private String institutionName;
    private String deptName;
    private String postAtTheTime;
    private String accountabilityPositions;
    private String gender;
    private String roleClassification;
    private String typeOfPerson;
    private String documentType;
    private String idNumber;
    private String highestEducation;
    private String thePoliticalLandscape;
    private String isDimission;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime timeOfDepartureStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime timeOfDepartureEnd;
    private String institutionalHierarchy;
    private String attributionOfViolations;
    private String businessArea;
    private String typeOfResponsibility;
    private String primaryAndSecondaryResponsibility;
    private String circumstancesOfTheViolation;
    private String rankAtTheTimeOfTheViolation;
    private String accountabilityRanks;
    private String violationCharacteristics;
    private String areasOfViolation;
    private String otherAreas;
    private String factsOfTheViolation;
    private String partyAttitude;
    private String processingBasis;
    private String accountabilityDocumentsAndNumbers;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime penaltyTimeStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime penaltyTimeEnd;
    private String penaltyPeriod;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime penaltyReleaseTimeStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime penaltyReleaseTimeEnd;
    private String whetherToSubmitSupervision;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime submissionTimeStart;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime submissionTimeEnd;
    private String generalHandling;
    private String disciplinaryAction;
    private String economicTreatment;
    private BigDecimal withholdingAmountMin;
    private BigDecimal withholdingAmountMax;
    private String withholdingInstructions;
    private String remark;
}
