package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 红色引擎-逾期扣分 */
@Data
@TableName("RE_OVERDUE_DEDUCTION")
public class ReOverdueDeduction {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 党组织ID */
    private Long orgId;
    /** 上报ID */
    private Long submitId;
    /** 扣分原因 */
    private String deductionReason;
    /** 扣分分值 */
    private BigDecimal deductionPoints;
    /** 扣分日期 */
    private LocalDate deductionDate;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
