package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对公存款账户（数据湖）
 */
@Data
@TableName("DATALAKE_XAN_PDL_C03_B_CORP_DEPOSIT_ACCT")
public class DlCorpDepositAcct {
    @TableField("Acct_No") private String acctNo;
    @TableField("Acct_Mdfr_No") private String acctMdfrNo;
    @TableField("Statis_Dt") private LocalDate statisDt;
    @TableField("Cust_Id") private String custId;
    @TableField("Biz_Cd") private String bizCd;
    @TableField("CRM_Biz_Id") private String crmBizId;
    @TableField("Subject_Cd") private String subjectCd;
    @TableField("Acct_Org_Id") private String acctOrgId;
    @TableField("Open_Org_Id") private String openOrgId;
    @TableField("Open_Teller_Id") private String openTellerId;
    @TableField("Close_Teller_Id") private String closeTellerId;
    @TableField("Open_Dt") private LocalDate openDt;
    @TableField("Close_Dt") private LocalDate closeDt;
    @TableField("Int_Start_Dt") private LocalDate intStartDt;
    @TableField("Mature_Dt") private LocalDate matureDt;
    @TableField("Lst_Int_Dt") private LocalDate lstIntDt;
    @TableField("Lat_Fin_Txn_Dt") private LocalDate latFinTxnDt;
    @TableField("Base_Acct_Ind") private String baseAcctInd;
    @TableField("Settle_Acct_Ind") private String settleAcctInd;
    @TableField("Apoint_Dpst_Ind") private String apointDpstInd;
    @TableField("InterBank_Ind") private String interBankInd;
    @TableField("Dpst_Withdraw_Ind") private String dpstWithdrawInd;
    @TableField("Cash_Exch_Ind") private String cashExchInd;
    @TableField("Dpst_Type_Cd") private String dpstTypeCd;
    @TableField("Acct_Type_Cd") private String acctTypeCd;
    @TableField("Int_Meth_Cd") private String intMethCd;
    @TableField("Renew_Withdraw_Int_Mode_Cd") private String renewWithdrawIntModeCd;
    @TableField("Acct_Stat_Cd") private String acctStatCd;
    @TableField("Bal_Direct_Cd") private String balDirectCd;
    @TableField("Term_Cd") private String termCd;
    @TableField("Rate_Period_Cd") private String ratePeriodCd;
    @TableField("Rate_Id") private String rateId;
    @TableField("Base_Int_Rate") private BigDecimal baseIntRate;
    @TableField("Overdraft_Rate") private BigDecimal overdraftRate;
    @TableField("Float_Rate") private BigDecimal floatRate;
    @TableField("Exe_Rate") private BigDecimal exeRate;
    @TableField("Time_Acct_Fst_Exe_Rate") private BigDecimal timeAcctFstExeRate;
    @TableField("Currency_Cd") private String currencyCd;
    @TableField("Overdraft_Bal") private BigDecimal overdraftBal;
    @TableField("Apoint_Save_Limit") private BigDecimal apointSaveLimit;
    @TableField("Curr_Bal") private BigDecimal currBal;
    @TableField("Curr_Bal_RMB") private BigDecimal currBalRmb;
    @TableField("Curr_M_Avg_Bal") private BigDecimal currMAvgBal;
    @TableField("Curr_M_Avg_Bal_RMB") private BigDecimal currMAvgBalRmb;
    @TableField("Curr_Y_Avg_Bal") private BigDecimal currYAvgBal;
    @TableField("Curr_Y_Avg_Bal_RMB") private BigDecimal currYAvgBalRmb;
}
