package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 个人存款账户（数据湖）
 */
@Data
@TableName("DATALAKE_XAN_C03_B_INDIV_DEPOSIT_ACCT")
public class DlIndivDepositAcct {
    @TableField("Acct_No") private String acctNo;
    @TableField("Acct_Mdfr_No") private String acctMdfrNo;
    @TableField("Statis_Dt") private LocalDate statisDt;
    @TableField("Cust_Id") private String custId;
    @TableField("Assoc_Acct_No") private String assocAcctNo;
    @TableField("Cert_No") private String certNo;
    @TableField("Acct_Org_Id") private String acctOrgId;
    @TableField("Open_Org_Id") private String openOrgId;
    @TableField("Close_Org_Id") private String closeOrgId;
    @TableField("Open_Teller_Id") private String openTellerId;
    @TableField("Close_Teller_Id") private String closeTellerId;
    @TableField("Open_Dt") private LocalDate openDt;
    @TableField("Close_Dt") private LocalDate closeDt;
    @TableField("Int_Start_Dt") private LocalDate intStartDt;
    @TableField("Mature_Dt") private LocalDate matureDt;
    @TableField("Lst_Int_Dt") private LocalDate lstIntDt;
    @TableField("Lst_Fin_Txn_Dt") private LocalDate lstFinTxnDt;
    @TableField("Biz_Cd") private String bizCd;
    @TableField("CRM_Biz_Id") private String crmBizId;
    @TableField("Subject_Cd") private String subjectCd;
    @TableField("Term_Cd") private String termCd;
    @TableField("Acct_Type_Cd") private String acctTypeCd;
    @TableField("Cert_Type_Cd") private String certTypeCd;
    @TableField("Dpst_Type_Cd") private String dpstTypeCd;
    @TableField("Marg_Class_Cd") private String margClassCd;
    @TableField("Acct_Stat_Cd") private String acctStatCd;
    @TableField("Bal_Direct_Cd") private String balDirectCd;
    @TableField("Rate_Period_Cd") private String ratePeriodCd;
    @TableField("Exe_Rate") private BigDecimal exeRate;
    @TableField("Overdraft_Rate") private BigDecimal overdraftRate;
    @TableField("Currency_Cd") private String currencyCd;
    @TableField("Cash_Exch_Ind") private String cashExchInd;
    @TableField("Overdraft_Limit") private BigDecimal overdraftLimit;
    @TableField("Freeze_Bal") private BigDecimal freezeBal;
    @TableField("Curr_Bal") private BigDecimal currBal;
    @TableField("Curr_Bal_RMB") private BigDecimal currBalRmb;
    @TableField("Curr_M_Avg_Bal") private BigDecimal currMAvgBal;
    @TableField("Curr_M_Avg_Bal_RMB") private BigDecimal currMAvgBalRmb;
    @TableField("Curr_Y_Avg_Bal") private BigDecimal currYAvgBal;
    @TableField("Curr_Y_Avg_Bal_RMB") private BigDecimal currYAvgBalRmb;
    @TableField("Allot_acct_no") private String allotAcctNo;
}
