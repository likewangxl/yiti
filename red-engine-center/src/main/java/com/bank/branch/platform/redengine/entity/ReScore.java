package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 红色引擎-评分 */
@Data
@TableName("RE_SCORE")
public class ReScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 党组织ID */
    private Long orgId;
    /** 关联上报ID */
    private Long submitId;
    /** 考核项编码 */
    private String itemCode;
    /** 考核年度 */
    private Integer scoreYear;
    /** 考核期间(YYYY-MM) */
    private String scorePeriod;
    private BigDecimal baseScore;
    private BigDecimal deductionScore;
    /** 最终得分 */
    private BigDecimal finalScore;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
