package com.bank.branch.platform.yundun.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 人员违规问责信息实体。 */
@Data
@TableName("t_accountability_for_violations")
public class AccountabilityViolation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String accountabilityForViolationsId;
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
    private LocalDateTime timeOfDeparture;
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
    private LocalDateTime penaltyTime;
    private String penaltyPeriod;
    private LocalDateTime penaltyReleaseTime;
    private String whetherToSubmitSupervision;
    private LocalDateTime submissionTime;
    private String generalHandling;
    private String disciplinaryAction;
    private String economicTreatment;
    private BigDecimal withholdingAmount;
    private String withholdingInstructions;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "1", delval = "0")
    private Integer inUse;
}
