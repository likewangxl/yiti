package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对公资产负债分配（数据湖）
 */
@Data
@TableName("DATALAKE_XAN_CRM_C06_CORP_ASSET_LIAB_ALLOT")
public class DlCorpAssetLiabAllot {
    @TableField("Acct_No") private String acctNo;
    @TableField("Allocater_Id") private String allocaterId;
    @TableField("Statis_Dt") private LocalDate statisDt;
    @TableField("Acct_Mdfr_No") private String acctMdfrNo;
    @TableField("CRM_Allocater_Id") private String crmAllocaterId;
    @TableField("Data_Src_Cd") private String dataSrcCd;
    @TableField("Cust_Id") private String custId;
    @TableField("Biz_Cd") private String bizCd;
    @TableField("CRM_Biz_Id") private String crmBizId;
    @TableField("Acct_Org_Id") private String acctOrgId;
    @TableField("IOU_No") private String iouNo;
    @TableField("IOU_Mdfr_No") private String iouMdfrNo;
    @TableField("Fix_Amt") private BigDecimal fixAmt;
    @TableField("Dyn_Scale") private BigDecimal dynScale;
    @TableField("By_Own_Ind") private String byOwnInd;
    @TableField("Currency_Cd") private String currencyCd;
    @TableField("Exe_Rate") private BigDecimal exeRate;
    @TableField("Simu_Rate") private BigDecimal simuRate;
    @TableField("Curr_Bal") private BigDecimal currBal;
    @TableField("Curr_Bal_RMB") private BigDecimal currBalRmb;
    @TableField("Curr_M_Avg_Bal") private BigDecimal currMAvgBal;
    @TableField("Curr_M_Avg_Bal_RMB") private BigDecimal currMAvgBalRmb;
    @TableField("Curr_Y_Avg_Bal") private BigDecimal currYAvgBal;
    @TableField("Curr_Y_Avg_Bal_RMB") private BigDecimal currYAvgBalRmb;
    @TableField("Curr_M_Int_Income_Expense") private BigDecimal currMIntIncomeExpense;
    @TableField("Curr_Y_Int_Income_Expense") private BigDecimal currYIntIncomeExpense;
    @TableField("Curr_M_FTP_Income_Expense") private BigDecimal currMFtpIncomeExpense;
    @TableField("Curr_Y_FTP_Income_Expense") private BigDecimal currYFtpIncomeExpense;
    @TableField("Curr_M_Simu_Profit1") private BigDecimal currMSimuProfit1;
    @TableField("Curr_Y_Simu_Profit1") private BigDecimal currYSimuProfit1;
    @TableField("Curr_M_Risk_Cost") private BigDecimal currMRiskCost;
    @TableField("Curr_Y_Risk_Cost") private BigDecimal currYRiskCost;
    @TableField("Curr_M_Simu_Profit2") private BigDecimal currMSimuProfit2;
    @TableField("Curr_Y_Simu_Profit2") private BigDecimal currYSimuProfit2;
    @TableField("Desc_Column") private String descColumn;
}
