package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M98 客户主档实体，对应存量 CUST_MASTER 表。
 *
 * <p>该表只承接 XAN_M98_CUST_STAT_SHOW3 的 T-1 客户号和名称同步，并供绩效等存量业务按
 * 客户号反显名称；客户营销数据必须写入 CUSTOMER_MARKET_CUSTOMER。</p>
 */
@Data
@TableName("CUST_MASTER")
public class M98CustMaster {

    /** 客户主档内部 ID。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** M98 客户号。 */
    private String custNo;

    /** 客户名称。 */
    private String custName;

    /** 客户状态。 */
    private String status;

    /** 逻辑删除标记。 */
    private Integer deleted;

    /** M98 数据统计日期。 */
    private String statisDt;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
