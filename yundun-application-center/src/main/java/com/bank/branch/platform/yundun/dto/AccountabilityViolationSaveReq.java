package com.bank.branch.platform.yundun.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 人员违规问责新增/编辑及 Excel 行模型。 */
@Data
@ExcelIgnoreUnannotated
public class AccountabilityViolationSaveReq {
    /** 导入模板中的列按截图顺序保留；未出现在模板的历史列统一置后。 */
    @ExcelProperty("主键(不可更改)")
    @Size(max = 100, message = "业务主键不能超过 100 个字符")
    private String accountabilityForViolationsId;
    @ExcelProperty("问责编号(*)") private String accountabilityNumber;
    @ExcelProperty("机构名称") private String institutionName;
    @ExcelProperty("问责来源") private String accountabilitySource;
    @ExcelProperty("具体来源") private String specificSource;
    @ExcelProperty("机构层级(*)") private String institutionalHierarchy;
    @ExcelProperty("辖属机构(*)") private String affiliatedInstitution;
    @ExcelProperty("所属条线(*)") private String attributionOfViolations;
    @ExcelProperty("被处罚人员名称(*)") private String name;
    @ExcelProperty("员工工号(*)")
    @NotBlank(message = "工号不能为空")
    private String workNumber;
    @ExcelProperty("性别(*)") private String gender;
    @ExcelProperty("人员类型(*)") private String typeOfPerson;
    @ExcelProperty("违规事实发生时职务、岗位(*)") private String postAtTheTime;
    @ExcelProperty("被问责时岗位、职务(*)") private String accountabilityPositions;
    @ExcelProperty("证件类型(*)") private String documentType;
    @ExcelProperty("证件号码(*)") private String idNumber;
    @ExcelProperty("最高学历(*)") private String highestEducation;
    @ExcelProperty("政治面貌(*)") private String thePoliticalLandscape;
    @ExcelProperty("是否离职(*)") private String isDimission;
    @ExcelProperty("离职时间") private LocalDateTime timeOfDeparture;
    @ExcelProperty("责任类型(*)") private String typeOfResponsibility;
    @ExcelProperty("违规特性") private String violationCharacteristics;
    @ExcelProperty("违规领域（旧）*") private String areasOfViolation;
    @ExcelProperty("违规领域（一级）") private String violationAreaLevelOne;
    @ExcelProperty("违规领域（二级）") private String violationAreaLevelTwo;
    @ExcelProperty("违规事实(*)") private String factsOfTheViolation;
    @ExcelProperty("处理依据(*)") private String processingBasis;
    @ExcelProperty("问责文件及文号(*)") private String accountabilityDocumentsAndNumbers;
    @ExcelProperty("处罚时间(*)") private LocalDateTime penaltyTime;
    @ExcelProperty("一般处理*") private String generalHandling;
    @ExcelProperty("纪律处分*") private String disciplinaryAction;
    @ExcelProperty("经济处理方式(*)") private String economicTreatment;
    @ExcelProperty("扣发金额(*)") private BigDecimal withholdingAmount;
    @ExcelProperty("扣发说明（落实情况）*") private String withholdingInstructions;
    @ExcelProperty("复议") private String reconsideration;
    @ExcelProperty("处罚期限(*)") private String penaltyPeriod;
    @ExcelProperty("处罚解除时间(*)") private LocalDateTime penaltyReleaseTime;
    @ExcelProperty("是否报送监管") private String whetherToSubmitSupervision;
    @ExcelProperty("报送时间") private LocalDateTime submissionTime;
    @ExcelProperty("备注") private String remark;

    /** 以下为不在新版导入模板中的历史列，导出时统一追加在模板列之后。 */
    @ExcelProperty("部门名称") private String deptName;
    @ExcelProperty("角色分类") private String roleClassification;
    @ExcelProperty("业务领域") private String businessArea;
    @ExcelProperty("主、次要责任") private String primaryAndSecondaryResponsibility;
    @ExcelProperty("违规情节") private String circumstancesOfTheViolation;
    @ExcelProperty("违规事实发生时职级") private String rankAtTheTimeOfTheViolation;
    @ExcelProperty("被问责时职级") private String accountabilityRanks;
    @ExcelProperty("其他领域") private String otherAreas;
    @ExcelProperty("当事人态度") private String partyAttitude;
    /** 系统维护字段，仅用于保存请求兼容性，不作为 Excel 列导入导出。 */
    @ExcelIgnore private Integer inUse;
}
