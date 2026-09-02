package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户标签触达周期上限规则，对应 CUST_TOUCH_LIMIT_RULE 表。
 *
 * <p>规则以 tagId 为唯一业务键；没有规则行的标签由服务层按默认值展示，
 * 不在查询时回写数据库。</p>
 */
@Data
@TableName("CUST_TOUCH_LIMIT_RULE")
public class TouchLimitRule {

    /** 主键，应用生成的 32 位 UUID。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 客户标签 ID，数据库唯一键保证一标签一规则。 */
    private String tagId;

    /** 周期单位：DAY、WEEK、MONTH、QUARTER、YEAR。 */
    private String cycleUnit;

    /** 周期内最多触达次数，业务范围 1..9999。 */
    private Integer maxTouches;

    /** 创建人。 */
    private String createdBy;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 最后更新人。 */
    private String updatedBy;

    /** 最后更新时间。 */
    private LocalDateTime updatedTime;
}
