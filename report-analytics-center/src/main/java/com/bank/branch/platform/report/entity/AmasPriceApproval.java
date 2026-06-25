package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 价格审批 AMAS_PRICE_APPROVAL 贫血实体（只读报表用）.
 *
 * <p>{@code PRICE_APPR_ID} 为物理主键；其余列依赖 common-db 全局
 * {@code map-underscore-to-camel-case} 自动驼峰映射，无需逐列 {@code @TableField}。
 * 全部为 varchar，金额/比率/日期均以字符串原样承载。</p>
 */
@Data
@TableName("AMAS_PRICE_APPROVAL")
public class AmasPriceApproval {

    /** 价格审批编号（主键）. */
    @TableId(value = "PRICE_APPR_ID", type = IdType.INPUT)
    private String priceApprId;

    /** 申请人工号. */
    private String applyUsername;

    /** 申请人姓名. */
    private String applyFullname;

    /** 申请人部门号. */
    private String applyDeptno;

    /** 申请时间. */
    private String applyTime;

    /** 客户号. */
    private String custId;

    /** 客户名称. */
    private String custName;

    /** 客户部门. */
    private String custDept;

    /** 金额. */
    private String money;

    /** 年限. */
    private String years;

    /** 执行利率. */
    private String executeRate;

    /** 基准利率. */
    private String localRate;

    /** 客户信息. */
    private String custInfo;

    /** 必要性说明. */
    private String necessExplain;

    /** 附件. */
    private String files;

    /** 浮动比例. */
    private String slidScale;

    /** 币种. */
    private String currency;

    /** 业务大类. */
    private String businCate;

    /** 业务类型. */
    private String businType;

    /** 对公/零售. */
    private String businOrRetail;

    /** 业务测算编号. */
    private String businCalcuNo;

    /** 备注. */
    private String remark;

    /** 审批进度（当前节点）. */
    private String apprProgress;

    /** 审批状态：0,待审批；1,通过；2,未通过. */
    private String apprStatus;

    /** 承诺金额. */
    private String promiseAmount;

    /** 承诺时间. */
    private String promiseTime;
}
