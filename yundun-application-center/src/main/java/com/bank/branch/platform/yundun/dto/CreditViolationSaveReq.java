package com.bank.branch.platform.yundun.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 信贷风险责任认定新增/编辑及 Excel 行模型。 */
@Data
@ExcelIgnoreUnannotated
public class CreditViolationSaveReq {
    /** 参考模板的首行视觉留空；使用空字符串以保证导入时 EasyExcel 能按四行表头匹配。 */
    @ExcelProperty(value = {"", "问责代码", "", ""}, index = 0) private String accountabilityCode;
    @ExcelProperty(value = {"", "客户名称", "", ""}, index = 1) private String clientName;
    @ExcelProperty(value = {"", "借据号", "", ""}, index = 2) private String iouNumber;
    @ExcelProperty(value = {"", "出险本金(万元)", "", ""}, index = 3) private BigDecimal insurancePrincipal;
    @ExcelProperty(value = {"", "核销金额(万元)", "", ""}, index = 4) private BigDecimal estimatedLoss;
    @ExcelProperty(value = {"", "认定结果有无责任(有/无)", "", ""}, index = 5) private String isResponsibility;
    @ExcelProperty(value = {"", "是否属于总行审核", "", ""}, index = 6) private String isAuditByHeadOffice;
    @ExcelProperty(value = {"", "是否属于普惠金融信贷业务", "", ""}, index = 7) private String isMicrofinanceCreditBusiness;
    @ExcelProperty(value = {"", "责任认定对象（姓名）", "", ""}, index = 8) private String responsiblePersonName;
    @ExcelProperty(value = {"", "员工工号", "", ""}, index = 9)
    @NotBlank(message = "员工工号不能为空")
    private String employeeNumber;
    @ExcelProperty(value = {"", "职务(岗位)", "", ""}, index = 10) private String position;
    @ExcelProperty(value = {"", "是否离职", "", ""}, index = 11) private String whetherToLeave;
    @ExcelProperty(value = {"", "责任认定对象所在机构", "机构名称", ""}, index = 12) private String institutionName;
    @ExcelProperty(value = {"", "责任认定对象所在机构", "机构层级", ""}, index = 13) private String institutionalLevel;
    @ExcelProperty(value = {"", "责任认定对象所在机构", "辖属机构", ""}, index = 14) private String affiliatedInstitution;
    @ExcelProperty(value = {"", "责任认定", "主观故意", ""}, index = 15) private String subjectiveIntent;
    @ExcelProperty(value = {"", "责任认定", "重大过失", ""}, index = 16) private String majorNegligence;
    @ExcelProperty(value = {"", "责任认定", "一般过失", ""}, index = 17) private String generalNegligence;
    @ExcelProperty(value = {"", "责任认定", "责任系数", ""}, index = 18) private String coefficientOfResponsibility;
    @ExcelProperty(value = {"", "责任认定", "责任占比", ""}, index = 19) private String responsibilityRatio;
    /** 新版模板新增的经营主责任人责任列。 */
    @ExcelProperty(value = {"", "责任认定", "是否承担经营主责任人责任", ""}, index = 20)
    private String operatingMainResponsiblePersonResponsibility;
    /** 与违规问责-经济处理的同名金额列区分，避免四行表头导入时按叶子标题误映射。 */
    @ExcelProperty(value = {"", "经济扣发", "扣发金额（元）", ""}, index = 21) private BigDecimal amountWithheld;
    @ExcelProperty(value = {"", "经济扣发", "是否为专项领域、一般过失且占比10%以下免于经济处理的情形", ""}, index = 22)
    private String specialAreaGeneralNegligenceExemption;
    @ExcelProperty(value = {"", "经济扣发", "是否为一般过失且扣减金额在500元（含）以下免于经济处理的情形", ""}, index = 23)
    private String generalNegligenceSmallAmountExemption;
    @ExcelProperty(value = {"", "经济扣发", "是否为经济扣减以部门前三年度薪酬为限的情形", ""}, index = 24)
    private String departmentSalaryLimitCondition;
    @ExcelProperty(value = {"", "违规问责", "一般处理", ""}, index = 25) private String generalHandling;
    @ExcelProperty(value = {"", "违规问责", "纪律处分", ""}, index = 26) private String disciplinaryAction;
    @ExcelProperty(value = {"", "违规问责", "经济处理", "处理类型"}, index = 27) private String processingType;
    @ExcelProperty(value = {"", "违规问责", "经济处理", "扣发金额（元）"}, index = 28)
    private BigDecimal amountWithheld2;
    @ExcelProperty(value = {"", "违规问责", "经济处理", "扣发说明"}, index = 29) private String withholdingInstructions;
    @ExcelProperty(value = {"", "违规问责", "问责文件名称及文号", ""}, index = 30) private String nameAndNumberOfAccountabilityDocument;

    /** 以下为不在新版导入模板中的历史列，导出时统一追加在模板列之后。 */
    @ExcelProperty(value = {"", "责任认定", "", ""}, index = 31) private String responsibilityDetermination;
    @ExcelProperty(value = {"", "是否免于经济扣发", "", ""}, index = 32) private String isEconomicDeduction;
    @ExcelProperty(value = {"", "回收返还（元）", "", ""}, index = 33) private BigDecimal recycleAndReturn;
    @ExcelProperty(value = {"", "是否虚扣", "", ""}, index = 34) private String isFalseBuckle;
    @ExcelProperty(value = {"", "其他处理", "", ""}, index = 35) private String otherProcessing;
    @ExcelProperty(value = {"", "责任认定时间", "", ""}, index = 36) private LocalDateTime responsibilityDeterminationTime;
    @ExcelProperty(value = {"", "是否报送银监机构", "", ""}, index = 37) private String isReportToTheBankingRegulatoryCommission;
    @ExcelProperty(value = {"", "报送时间", "", ""}, index = 38) private LocalDateTime submissionTime;
    @ExcelProperty(value = {"", "备注", "", ""}, index = 39) private String remark;
    /** 系统维护字段，仅用于保存请求兼容性，不作为 Excel 列导入导出。 */
    @ExcelIgnore private Integer inUse;
}
