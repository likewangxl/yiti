package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 个人分配关系（数据湖）
 */
@Data
@TableName("DATALAKE_XAN_CRM_C06_INDIV_ALLOT_RELA")
public class DlIndivAllotRela {
    @TableField("TX_DATE") private String txDate;
    @TableField("Allot_Biz_Type_Cd") private String allotBizTypeCd;
    @TableField("Allot_Id_Type_Cd") private String allotIdTypeCd;
    @TableField("Allot_Id") private String allotId;
    @TableField("Allot_Id_Mdfr_No") private String allotIdMdfrNo;
    @TableField("CRM_Allocater_Id") private String crmAllocaterId;
    @TableField("Allot_Mode_Cd") private String allotModeCd;
    @TableField("Statis_Y_M") private String statisYM;
    @TableField("Eff_Dt") private LocalDate effDt;
    @TableField("Dvp_Ratio") private BigDecimal dvpRatio;
    @TableField("Fix_Amt") private BigDecimal fixAmt;
    @TableField("Expire_Dt") private LocalDate expireDt;
}
