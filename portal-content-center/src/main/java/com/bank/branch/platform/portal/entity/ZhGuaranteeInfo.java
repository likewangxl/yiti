package com.bank.branch.platform.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 担保信息实体，对应 yiti 库 {@code zh_guarantee_info} 表。
 *
 * <p>页面字段 → 物理列映射（业务方表列含义较泛，此处按担保查询页面口径绑定，前端表单/列表与本映射保持一致）：
 * <ul>
 *   <li>客户名称           → client_name</li>
 *   <li>业务额度（万元）    → notional_amount</li>
 *   <li>剩余额度（万元）    → occupy_notional_amount</li>
 *   <li>融资额度（万元）    → usablenominalsum</li>
 *   <li>授信到期日          → last_expire</li>
 *   <li>经办人（userName）  → operator</li>
 *   <li>数据变动日期（只读）→ create_time</li>
 *   <li>变更日期（只读）    → update_time</li>
 * </ul>
 *
 * <p>金额/日期列在物理表均为 varchar，这里统一以 String 承载，保留业务方录入原值不做数值化转换。</p>
 */
@Data
@TableName("zh_guarantee_info")
public class ZhGuaranteeInfo {

    /** 主键，自增，对应 id */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 客户号，对应 client_num（注意：物理列名为 client_num，非 client_no） */
    @TableField("client_num")
    private String clientNo;

    /** 客户名称，对应 client_name */
    @TableField("client_name")
    private String clientName;

    /** 客户基础id，对应 basic_id（合同导入时来源列为空，保持 null） */
    @TableField("basic_id")
    private String basicId;

    /** 额度类型，对应 amount_type（合同导入时取 credittypeflag） */
    @TableField("amount_type")
    private String amountType;

    /** 业务额度（万元），对应 notional_amount */
    @TableField("notional_amount")
    private String notionalAmount;

    /** 已占用敞口金额，对应 occupy_exposure_amount（合同导入时取 exposurebalance） */
    @TableField("occupy_exposure_amount")
    private String occupyExposureAmount;

    /** 可用敞口金额，对应 usableexposuresum（合同导入时取 usableexposuresum） */
    @TableField("usableexposuresum")
    private String usableExposureSum;

    /** 到期日，对应 expired（合同导入时取 maturity 额度到期日） */
    @TableField("expired")
    private String expired;

    /** 额度生效日，对应 start 列（SQL 保留字，加反引号；合同导入时取 putoutdate） */
    @TableField("`start`")
    private String start;

    /** 经办机构，对应 organ（合同导入时取 operateorgid） */
    @TableField("organ")
    private String organ;

    /** 剩余额度（万元），对应 occupy_notional_amount */
    @TableField("occupy_notional_amount")
    private String occupyNotionalAmount;

    /** 融资额度（万元），对应 usablenominalsum */
    @TableField("usablenominalsum")
    private String usableNominalSum;

    /** 授信到期日，对应 last_expire */
    @TableField("last_expire")
    private String lastExpire;

    /** 经办人（属性名 userName），对应 operator 列 */
    @TableField("operator")
    private String userName;

    /** 创建人工号，对应 create_user */
    @TableField("create_user")
    private String createUser;

    /** 数据变动日期（入库时间戳），对应 create_time */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 记录类型，对应 type（默认 '1'） */
    @TableField("type")
    private String type;

    /** 变更日期（最近修改时间，varchar），对应 update_time */
    @TableField("update_time")
    private String updateTime;
}
