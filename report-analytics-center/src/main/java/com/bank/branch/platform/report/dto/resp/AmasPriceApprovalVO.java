package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/**
 * 价格审批列表行 / 详情视图对象（字段名与实体一致，便于 BeanUtils.copyProperties）.
 */
@Data
public class AmasPriceApprovalVO {

    /** 价格审批编号. */
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
