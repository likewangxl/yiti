package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对公贷款账户（数据湖）
 */
@Data
@TableName("DATALAKE_XAN_PDL_C03_B_CORP_LOAN_ACCT")
public class DlCorpLoanAcct {
    @TableField("Acct_No") private String acctNo;
    @TableField("Acct_Mdfr_No") private String acctMdfrNo;
    @TableField("Statis_Dt") private LocalDate statisDt;
    @TableField("IOU_No") private String iouNo;
    @TableField("IOU_Mdfr_No") private String iouMdfrNo;
    @TableField("Cust_Id") private String custId;
    @TableField("Biz_Cd") private String bizCd;
    @TableField("CRM_Biz_Id") private String crmBizId;
    @TableField("Subject_Cd") private String subjectCd;
    @TableField("Acct_Org_Id") private String acctOrgId;
    @TableField("Open_Org_Id") private String openOrgId;
    @TableField("Close_Org_Id") private String closeOrgId;
    @TableField("Open_Teller_Id") private String openTellerId;
    @TableField("Close_Teller_Id") private String closeTellerId;
    @TableField("Open_Dt") private LocalDate openDt;
    @TableField("Close_Dt") private LocalDate closeDt;
    @TableField("Lst_Int_Dt") private LocalDate lstIntDt;
    @TableField("Lst_Fin_Txn_Dt") private LocalDate lstFinTxnDt;
    @TableField("Int_Ind") private String intInd;
    @TableField("Int_Meth_Cd") private String intMethCd;
    @TableField("Loan_Class_Cd") private String loanClassCd;
    @TableField("Acct_Class_Cd") private String acctClassCd;
    @TableField("Acct_Type_Cd") private String acctTypeCd;
    @TableField("Term_Cd") private String termCd;
    @TableField("Acct_Stat_Cd") private String acctStatCd;
    @TableField("Bal_Direct_Cd") private String balDirectCd;
    @TableField("Rate_Period_Cd") private String ratePeriodCd;
    @TableField("Rate_Id") private String rateId;
    @TableField("Base_Int_Rate") private BigDecimal baseIntRate;
    @TableField("Fst_Exe_Rate") private BigDecimal fstExeRate;
    @TableField("Exe_Rate") private BigDecimal exeRate;
    @TableField("Float_Rate") private BigDecimal floatRate;
    @TableField("Currency_Cd") private String currencyCd;
    @TableField("Curr_Bal") private BigDecimal currBal;
    @TableField("Curr_Bal_RMB") private BigDecimal currBalRmb;
    @TableField("Curr_M_Avg_Bal") private BigDecimal currMAvgBal;
    @TableField("Curr_M_Avg_Bal_RMB") private BigDecimal currMAvgBalRmb;
    @TableField("Curr_Y_Avg_Bal") private BigDecimal currYAvgBal;
    @TableField("Curr_Y_Avg_Bal_RMB") private BigDecimal currYAvgBalRmb;
}
