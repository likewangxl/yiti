package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 业务合同信息实体，对应 yiti 库 {@code ccms_business_contract} 表。
 *
 * <p>担保信息新增时的数据来源表：按客户号（customerid）+ 客户名称（customername）反查该客户名下
 * 全部授信/业务合同记录，命中后批量映射落库到 {@code zh_guarantee_info}（见
 * {@code GuaranteeService#create}）。本表为只读引用，portal 仅做查询，不做写入。</p>
 *
 * <p>金额列为 {@code decimal(24,6)}，单位「元」，映射到 zh_guarantee_info 的 varchar 金额列时按「元」
 * 原值存储（与手工录入「万元 ×10000 落库」口径一致：库内统一「元」，展示层 ÷10000 显示「万元」）。</p>
 */
@Data
@TableName("ccms_business_contract")
public class CcmsBusinessContract {

    /** 主键，自增，对应 id */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 客户基础id：ccms_business_contract 物理表无此列，仅为对齐 toGuarantee 映射，恒为 null */
    @TableField(exist = false)
    private String basicId;

    /** 客户号，对应 customerid */
    @TableField("customerid")
    private String customerId;

    /** 客户名称，对应 customername */
    @TableField("customername")
    private String customerName;

    /** 额度类型，对应 credittypeflag */
    @TableField("credittypeflag")
    private String creditTypeFlag;

    /** 已占用金额（敞口），对应 exposurebalance */
    @TableField("exposurebalance")
    private BigDecimal exposureBalance;

    /** 名义金额，对应 businesssum2 */
    @TableField("businesssum2")
    private BigDecimal businessSum2;

    /** 可用敞口金额，对应 usableexposuresum */
    @TableField("usableexposuresum")
    private BigDecimal usableExposureSum;

    /** 可用名义金额，对应 usablenominalsum */
    @TableField("usablenominalsum")
    private BigDecimal usableNominalSum;

    /** 额度生效日期，对应 putoutdate */
    @TableField("putoutdate")
    private String putoutDate;

    /** 额度到期日，对应 maturity */
    @TableField("maturity")
    private String maturity;

    /** 业务最后到期日，对应 termdate3 */
    @TableField("termdate3")
    private String termDate3;

    /** 经办机构，对应 operateorgid */
    @TableField("operateorgid")
    private String operateOrgId;

    /** 经办人，对应 operateuserid */
    @TableField("operateuserid")
    private String operateUserId;
}
