package com.bank.branch.platform.customer.dto.resp;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 已审批线索 Excel 行，字段与录入/审批详情保持一致。 */
@Data
public class LeadApprovalExportRow {
    @ExcelProperty("线索编号") private String leadNo;
    @ExcelProperty("线索类型") private String leadType;
    @ExcelProperty("客户号") private String custNo;
    @ExcelProperty("客户名称") private String custName;
    @ExcelProperty("统一社会信用代码") private String unifiedCreditCode;
    @ExcelProperty("联系人") private String contactPerson;
    @ExcelProperty("联系电话") private String contactMobile;
    @ExcelProperty("所属行业") private String industry;
    @ExcelProperty("集团类型") private String groupType;
    @ExcelProperty("集团名称") private String groupName;
    @ExcelProperty("客户类型") private String customerType;
    @ExcelProperty("是否基石客户") private String keystone;
    @ExcelProperty("企业类型") private String enterpriseType;
    @ExcelProperty("是否开户") private String accountOpened;
    @ExcelProperty("是否触达限制") private String touchRestricted;
    @ExcelProperty("客户说明") private String customerDesc;
    @ExcelProperty("授信金额(元)") private BigDecimal creditAmount;
    @ExcelProperty("授信敞口金额(元)") private BigDecimal creditExposureAmount;
    @ExcelProperty("线索来源") private String leadSource;
    @ExcelProperty("分配方式") private String distributionMode;
    @ExcelProperty("主办客户经理工号") private String mainManagerId;
    @ExcelProperty("主办机构") private String mainManagerOrgId;
    @ExcelProperty("标签ID") private String tagIds;
    @ExcelProperty("审批结果") private String leadStatus;
    @ExcelProperty("提交人") private String submittedBy;
    @ExcelProperty("提交时间") private LocalDateTime submittedTime;
    @ExcelProperty("审批人") private String reviewedBy;
    @ExcelProperty("审批时间") private LocalDateTime reviewedTime;
    @ExcelProperty("驳回原因") private String rejectReason;
    @ExcelProperty("备注") private String remark;
}
